package com.yunfeil161.ailibrary.auth.service.impl;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.dto.LoginInfo;
import com.yunfeil161.ailibrary.auth.dto.RegisterParam;
import com.yunfeil161.ailibrary.auth.mapper.LoginMapper;
import com.yunfeil161.ailibrary.auth.service.LoginService;
import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import com.yunfeil161.ailibrary.common.exception.TooManyRequestsException;
import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.config.security.VerificationCodeUtils;
import com.yunfeil161.ailibrary.config.security.jwt.JwtUtils;
import com.yunfeil161.ailibrary.config.security.role.entity.Role;
import com.yunfeil161.ailibrary.config.security.role.enums.RoleStatus;
import com.yunfeil161.ailibrary.config.security.role.service.RoleQueryService;
import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Member;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import com.yunfeil161.ailibrary.identity.mapper.DeptMapper;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.identity.service.MemberService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
    "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
  );

  private final com.yunfeil161.ailibrary.recommendation.service.RecommendationWarmPublisher recommendationWarmPublisher;
  private final LoginMapper loginMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtils jwtUtils;
  private final LoginSessionService loginSessionService;
  private final com.yunfeil161.ailibrary.auth.service.VerificationCodeStore verificationCodes;
  private final com.yunfeil161.ailibrary.auth.service.LoginAttemptLimiter loginAttempts;
  private final JavaMailSender javaMailSender;
  private final MemberMapper memberMapper;
  private final MemberService memberService;
  private final DeptMapper deptMapper;
  private final RoleQueryService roleQueryService;
  private final com.yunfeil161.ailibrary.policy.service.PolicyState policyState;

  @Value("${spring.mail.username:}")
  private String mailSender;

  /** 仅用于启动后的完整性检查，不记录或返回授权码内容。 */
  @Value("${spring.mail.password:}")
  private String mailPassword;

  @Override
  public LoginInfo login(String email, String password) {
    // 统一邮箱规范，并避免空值或超长BCrypt输入变成500。
    if (
      email == null ||
      password == null ||
      password.isBlank() ||
      password.getBytes(StandardCharsets.UTF_8).length > 72
    ) {
      throw new UnauthorizedException("邮箱或密码错误");
    }
    String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
    if (
      normalizedEmail.length() > 254 ||
      !EMAIL_PATTERN.matcher(normalizedEmail).matches()
    ) throw new UnauthorizedException("邮箱或密码错误");
    loginAttempts.reserve(normalizedEmail);
    Member member = loginMapper.findByEmail(normalizedEmail);

    if (
      member == null ||
      member.getPasswordHash() == null ||
      !passwordEncoder.matches(password, member.getPasswordHash())
    ) {
      throw new UnauthorizedException("邮箱或密码错误");
    }
    loginAttempts.succeeded(normalizedEmail);
    // AI 智能体不使用成员登录；兼容旧数据时也必须拒绝签发新会话。
    if (member.getMemberType() == MemberType.AI_LIBRARIAN) {
      throw new ForbiddenException("AI服务身份已停用");
    }
    LoginInfo info = new LoginInfo();
    // 登录仍可成功，但未确认最新须知的会话只能访问受限接口。
    info.setRequiresRuleConsent(policyState.requiresConsent(member));
    info.setCurrentRuleVersion(policyState.ruleVersion());
    info.setName(member.getName());
    info.setId(member.getId());
    info.setEmail(member.getEmail());

    if (member.getAccountStatus() != AccountStatus.ACTIVE) {
      throw new ForbiddenException("账号已停用");
    }

    Role role = roleQueryService.getRoleById(member.getRoleId());
    if (role.getStatus() != RoleStatus.ACTIVE) {
      throw new ForbiddenException("角色已停用");
    }
    String jti = UUID.randomUUID().toString();
    Map<String, Object> claims = Map.of(
      "memberId",
      member.getId(),
      "roleId",
      member.getRoleId(),
      "memberType",
      member.getMemberType(),
      "roleCode",
      role.getCode()
    );

    LoginSession session = new LoginSession(
      jti,
      member.getId(),
      member.getRoleId(),
      member.getMemberType(),
      role.getCode(),
      member.getEmail(),
      member.getName()
    );

    String token = jwtUtils.generateToken(claims, jti);
    loginSessionService.save(jti, session, jwtUtils.getExpirationDuration());
    // 推荐预热与认证成功独立；消费者只从数据库读取当前批次。
    if (
      session.getMemberType() != MemberType.AI_LIBRARIAN
    ) recommendationWarmPublisher.request(session.getMemberId());

    info.setToken(token);
    info.setMemberType(member.getMemberType());
    info.setRoleId(member.getRoleId());
    info.setRoleCode(role.getCode());

    return info;
  }

  @Override
  public void logout() {
    LoginSession session = LoginUserContext.get();
    loginSessionService.delete(session.getJti());
    LoginUserContext.clear();
  }

  @Override
  public void loginCode(String email) {
    // 1. 校验并规范化邮箱，避免同一邮箱因大小写或空格形成多个 Redis 键。
    if (
      email == null ||
      email.trim().length() > 254 ||
      !EMAIL_PATTERN.matcher(email.trim()).matches()
    ) {
      throw new WrongFormatException("邮箱格式错误");
    }
    String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

    // 邮件账号未配置时直接返回明确错误，不生成验证码和限流键。
    if (
      !StringUtils.hasText(mailSender) || !StringUtils.hasText(mailPassword)
    ) {
      throw new ServiceUnavailableException(
        "邮件服务未配置，请设置 MAIL_USERNAME 和 MAIL_PASSWORD"
      );
    }

    // 2. 原子设置60秒冷却、10分钟验证码，并重置本轮错误次数。
    String code = VerificationCodeUtils.generateSixDigitCode();
    String nonce = verificationCodes.issue(normalizedEmail, code);

    // 4. 发送邮件。发送失败时撤销 Redis 数据，使用户可以立即重试。
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(mailSender);
    message.setTo(normalizedEmail);
    message.setSubject("AI Library 验证码");
    message.setText(
      "你的验证码是：" + code + "，10分钟内有效。请勿向他人透露该验证码。"
    );

    try {
      javaMailSender.send(message);
    } catch (MailException e) {
      verificationCodes.revokeFailedSend(normalizedEmail, code, nonce);
      throw new ServiceUnavailableException(
        "验证码邮件发送失败，请稍后重试",
        e
      );
    }
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = "'lock:resource:member:register:' + (#registerParam != null ? #registerParam.email : 'invalid')"
  )
  @Audited(resource = AuditResource.MEMBER)
  public Long registerReader(RegisterParam registerParam) {
    validateRegisterParam(registerParam, MemberType.READER);
    Reader reader = new Reader();
    populateBaseMember(reader, registerParam, MemberType.READER, "READER");
    reader.setPersonalizationEnabled(false);
    reader.setRecommendationNotificationsEnabled(false);

    insertMember(reader);
    if (memberMapper.insertReaderSettings(reader.getId(), reader) != 1) {
      throw new IllegalStateException("初始化读者设置失败");
    }
    return reader.getId();
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = "'lock:resource:member:register:' + (#registerParam != null ? #registerParam.email : 'invalid')"
  )
  @Audited(resource = AuditResource.MEMBER)
  public Long registerEmployee(RegisterParam registerParam) {
    validateRegisterParam(registerParam, MemberType.EMPLOYEE);
    Employee employee = new Employee();
    populateBaseMember(
      employee,
      registerParam,
      MemberType.EMPLOYEE,
      // 自主注册不能自授馆员权限；任职审批通过后再授予岗位角色。
      "READER"
    );
    employee.setDepartmentId(registerParam.getDepartmentId());
    employee.setPositionName(registerParam.getPositionName().trim());
    employee.setHiredOn(LocalDate.now());
    employee.setEmploymentStatus(EmploymentStatus.SUSPENDED);

    insertMember(employee);
    employee.setEmployeeNo(generateEmployeeNo(employee.getId()));
    if (memberMapper.insertEmployeeProfile(employee.getId(), employee) != 1) {
      throw new IllegalStateException("初始化员工档案失败");
    }
    return employee.getId();
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = "'lock:resource:member:password:' + (#param != null ? #param.email : 'invalid')"
  )
  public void resetPassword(RegisterParam param) {
    if (
      param == null ||
      param.getEmail() == null ||
      param.getEmail().trim().length() > 254 ||
      !EMAIL_PATTERN.matcher(param.getEmail().trim()).matches()
    ) {
      throw new WrongFormatException("邮箱格式错误");
    }
    if (param.getPassword() == null || param.getPassword().isBlank()) {
      throw new WrongFormatException("新密码不能为空");
    }
    if (
      param.getCode() == null || !param.getCode().trim().matches("[0-9]{6}")
    ) {
      throw new WrongFormatException("验证码不能为空");
    }

    validateNewPassword(param.getPassword());
    String normalizedEmail = param
      .getEmail()
      .trim()
      .toLowerCase(java.util.Locale.ROOT);
    verificationCodes.consume(normalizedEmail, param.getCode().trim());

    Member member = loginMapper.findByEmail(normalizedEmail);
    if (member == null) {
      throw new NotFoundException("邮箱尚未注册");
    }

    memberService.updatePassword(
      member.getId(),
      passwordEncoder.encode(param.getPassword())
    );

    // 验证码已在写入密码前原子消费，不再执行可能误删新验证码的普通DEL。
  }

  private void validateRegisterParam(
    RegisterParam param,
    MemberType expectedMemberType
  ) {
    if (
      param == null ||
      param.getMemberType() == null ||
      param.getMemberType() != expectedMemberType
    ) {
      throw new WrongFormatException("注册身份与注册流程不匹配");
    }
    if (
      param.getEmail() == null ||
      param.getEmail().trim().length() > 254 ||
      !EMAIL_PATTERN.matcher(param.getEmail().trim()).matches()
    ) {
      throw new WrongFormatException("邮箱格式错误");
    }
    if (
      param.getName() == null ||
      param.getName().trim().isEmpty() ||
      param.getName().trim().length() > 80
    ) {
      throw new WrongFormatException("姓名不能为空");
    }
    if (param.getPassword() == null || param.getPassword().isBlank()) {
      throw new WrongFormatException("密码不能为空");
    }
    if (
      param.getCode() == null || !param.getCode().trim().matches("[0-9]{6}")
    ) {
      throw new WrongFormatException("验证码不能为空");
    }

    validateNewPassword(param.getPassword());
    String normalizedEmail = param
      .getEmail()
      .trim()
      .toLowerCase(java.util.Locale.ROOT);
    // 先验证邮箱所有权，再返回任何姓名/邮箱唯一性信息。
    verificationCodes.consume(normalizedEmail, param.getCode().trim());
    if (
      memberMapper.countByIdentity(
        param.getName().trim(),
        normalizedEmail,
        null,
        null
      ) > 0
    ) {
      throw new ConflictException("姓名或邮箱已存在");
    }

    if (expectedMemberType == MemberType.EMPLOYEE) {
      if (
        param.getDepartmentId() == null ||
        param.getPositionName() == null ||
        param.getPositionName().isBlank() ||
        param.getPositionName().trim().length() > 80
      ) {
        throw new WrongFormatException("员工部门和职位不能为空");
      }
      if (deptMapper.getDepartmentById(param.getDepartmentId()) == null) {
        throw new WrongFormatException("所选部门不存在");
      }
    }
  }

  private void populateBaseMember(
    Member member,
    RegisterParam param,
    MemberType memberType,
    String roleCode
  ) {
    member.setName(param.getName().trim());
    member.setEmail(param.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
    member.setPasswordHash(passwordEncoder.encode(param.getPassword()));
    member.setMemberType(memberType);
    member.setRoleId(roleQueryService.getRoleByCode(roleCode).getId());
    roleQueryService.requireActiveRole(member.getRoleId());
    member.setAccountStatus(AccountStatus.ACTIVE);
    member.setQualificationStatus(QualificationStatus.NOT_APPLIED);
    member.setEmailVerifiedAt(LocalDateTime.now());
    member.setAcceptedRuleVersion(null);
    member.setBorrowedBookCount(0);
    member.setPermanentBorrowLimit(5);
  }

  private void insertMember(Member member) {
    if (memberMapper.insertMember(member) != 1 || member.getId() == null) {
      throw new IllegalStateException("新增成员失败");
    }
  }

  /**
   * 使用已经由数据库生成的成员ID分配工号，保证工号稳定且不会重复。
   */
  private String generateEmployeeNo(Long memberId) {
    return "EMP-" + String.format("%08d", memberId);
  }

  private void validateNewPassword(String password) {
    if (
      password == null ||
      password.length() < 8 ||
      password.getBytes(StandardCharsets.UTF_8).length > 72
    ) {
      throw new WrongFormatException(
        "密码至少8个字符，且UTF-8长度不能超过72字节"
      );
    }
  }
}
