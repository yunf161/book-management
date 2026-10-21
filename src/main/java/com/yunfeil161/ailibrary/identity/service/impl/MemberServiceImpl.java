package com.yunfeil161.ailibrary.identity.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.role.service.RoleQueryService;
import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import com.yunfeil161.ailibrary.identity.service.MemberService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

  private static final String READER_CACHE_KEY_PREFIX = "member:reader:";
  private static final String EMPLOYEE_CACHE_KEY_PREFIX = "member:employee:";
  private static final long MEMBER_CACHE_TTL_MINUTES = 10L;

  private final com.yunfeil161.ailibrary.recommendation.service.TagService tagService;
  private final com.yunfeil161.ailibrary.recommendation.service.RecommendationState recommendationState;
  private final MemberMapper memberMapper;
  private final RoleQueryService roleQueryService;
  private final CacheClient cacheClient;
  private final LoginSessionService loginSessionService;

  @Override
  @DistributedLock(
    keys = "'lock:resource:member:' + (#reader != null && #reader.id != null ? #reader.id : 'reader-query')",
    mode = LockMode.READ
  )
  public PageResult<Reader> getReaders(
    Reader reader,
    Integer page,
    Integer pageSize
  ) {
    if (canUseIdCache(reader)) {
      Reader result = cacheClient.queryWithPassThrough(
        READER_CACHE_KEY_PREFIX,
        reader.getId(),
        Reader.class,
        this::queryReaderById,
        MEMBER_CACHE_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      if (result == null) {
        throw new NotFoundException("未找到读者信息");
      }
      return new PageResult<>(1L, List.of(result));
    }
    startPage(page, pageSize);
    List<Reader> rows = memberMapper.getReadersByPage(reader);
    PageInfo<Reader> info = new PageInfo<>(rows);
    return new PageResult<>(info.getTotal(), info.getList());
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:member:' + (#employee != null && #employee.id != null ? #employee.id : 'employee-query')",
    mode = LockMode.READ
  )
  public PageResult<Employee> getEmployees(
    Employee employee,
    Integer page,
    Integer pageSize
  ) {
    if (canUseIdCache(employee)) {
      Employee result = cacheClient.queryWithPassThrough(
        EMPLOYEE_CACHE_KEY_PREFIX,
        employee.getId(),
        Employee.class,
        this::queryEmployeeById,
        MEMBER_CACHE_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      if (result == null) {
        throw new NotFoundException("未找到员工信息");
      }
      return new PageResult<>(1L, List.of(result));
    }
    startPage(page, pageSize);
    List<Employee> rows = memberMapper.getEmployeesByPage(employee);
    PageInfo<Employee> info = new PageInfo<>(rows);
    return new PageResult<>(info.getTotal(), info.getList());
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:member:create-reader'")
  @Audited(resource = AuditResource.MEMBER, id = "")
  public Reader addReader(Reader reader, List<String> interests) {
    validateMember(reader, MemberType.READER, true);
    insertMember(reader);
    memberMapper.insertReaderSettings(reader.getId(), reader);
    replaceInterests(reader.getId(), interests);
    cacheClient.deleteRequired(READER_CACHE_KEY_PREFIX + reader.getId());
    return findReader(reader.getId());
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:member:create-employee'")
  @Audited(resource = AuditResource.MEMBER, id = "")
  public Employee addEmployee(Employee employee) {
    validateMember(employee, MemberType.EMPLOYEE, true);
    if (isAdminRole(employee.getRoleId())) {
      throw new WrongFormatException("系统管理员暂不支持新增");
    }
    if (
      isBlank(employee.getEmployeeNo()) ||
      employee.getDepartmentId() == null ||
      isBlank(employee.getPositionName()) ||
      employee.getHiredOn() == null
    ) {
      throw new WrongFormatException("员工编号、部门、职位和入职日期不能为空");
    }
    insertMember(employee);
    if (memberMapper.insertEmployeeProfile(employee.getId(), employee) != 1) {
      throw new IllegalStateException("新增员工档案失败");
    }
    cacheClient.deleteRequired(EMPLOYEE_CACHE_KEY_PREFIX + employee.getId());
    return findEmployee(employee.getId());
  }

  @Override
  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:member:' + #id",
      "'lock:resource:recommendation:member:' + #id",
    }
  )
  @Audited(resource = AuditResource.MEMBER, id = "#id")
  public Reader updateReader(
    Long id,
    Reader reader,
    List<String> interests,
    Long expectedVersion
  ) {
    validateUpdate(id, reader, MemberType.READER, expectedVersion);
    ensureUnique(reader, id);
    updateMember(id, reader, expectedVersion);
    boolean personalizationChanged = recommendationState.personalizationChanged(
      id,
      reader.getPersonalizationEnabled()
    );
    memberMapper.updateReaderSettings(id, reader);
    if (interests != null) replaceInterests(id, interests);
    else if (personalizationChanged) recommendationState.invalidate(id);
    cacheClient.deleteRequired(READER_CACHE_KEY_PREFIX + id);
    return findReader(id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:member:' + #id")
  @Audited(resource = AuditResource.MEMBER, id = "#id")
  public Employee updateEmployee(
    Long id,
    Employee employee,
    Long expectedVersion
  ) {
    com.yunfeil161.ailibrary.identity.entity.Member existing =
      memberMapper.findMemberById(id);
    if (existing != null && isAdminRole(existing.getRoleId())) {
      throw new WrongFormatException("系统管理员暂不支持更新");
    }
    validateUpdate(id, employee, MemberType.EMPLOYEE, expectedVersion);
    ensureUnique(employee, id);
    updateMember(id, employee, expectedVersion);
    memberMapper.updateEmployeeProfile(id, employee);
    cacheClient.deleteRequired(EMPLOYEE_CACHE_KEY_PREFIX + id);
    return findEmployee(id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:member:' + #id")
  @Audited(resource = AuditResource.MEMBER, id = "#id")
  public void updatePassword(Long id, String passwordHash) {
    if (id == null || passwordHash == null || passwordHash.isBlank()) {
      throw new WrongFormatException("成员ID和密码摘要不能为空");
    }
    if (memberMapper.updatePassword(id, passwordHash) != 1) {
      throw new NotFoundException("成员不存在");
    }

    // 统一清除两种人员按 ID 缓存，保证后续查询读取最新版本。
    cacheClient.deleteRequired(READER_CACHE_KEY_PREFIX + id);
    cacheClient.deleteRequired(EMPLOYEE_CACHE_KEY_PREFIX + id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:member:' + #id")
  @Audited(resource = AuditResource.MEMBER, id = "#id")
  public String deleteReader(Long id) {
    Reader existing = findReader(id);
    disableMember(existing);
    evictMemberCaches(id);
    loginSessionService.deleteAllForMember(id);
    return existing.getName();
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:member:' + #id")
  @Audited(resource = AuditResource.MEMBER, id = "#id")
  public String deleteEmployee(Long id) {
    com.yunfeil161.ailibrary.identity.entity.Member member =
      memberMapper.findMemberById(id);
    if (member != null && isAdminRole(member.getRoleId())) {
      throw new WrongFormatException("系统管理员暂不支持删除");
    }
    Employee existing = findEmployee(id);
    disableMember(existing);
    evictMemberCaches(id);
    loginSessionService.deleteAllForMember(id);
    return existing.getName();
  }

  private void insertMember(
    com.yunfeil161.ailibrary.identity.entity.Member member
  ) {
    ensureUnique(member, null);
    rejectDirectTemporaryQuota(member);
    // 管理员创建成员不能代替成员接受借阅须知。
    member.setAcceptedRuleVersion(null);
    member.setBorrowedBookCount(0);
    if (
      member.getPermanentBorrowLimit() != null &&
      member.getPermanentBorrowLimit() < 0
    ) {
      throw new WrongFormatException("最大借阅数量不能小于0");
    }
    if (member.getRoleId() == null) {
      Long defaultRoleId = roleQueryService
        .getRoleByCode(defaultRoleCode(member.getMemberType()))
        .getId();
      member.setRoleId(
        roleQueryService.requireActiveRole(defaultRoleId).getId()
      );
    } else {
      roleQueryService.requireActiveRole(member.getRoleId());
    }
    if (memberMapper.insertMember(member) != 1 || member.getId() == null) {
      throw new IllegalStateException("新增成员失败");
    }
  }

  private void updateMember(
    Long id,
    com.yunfeil161.ailibrary.identity.entity.Member member,
    Long expectedVersion
  ) {
    com.yunfeil161.ailibrary.identity.entity.Member existing =
      memberMapper.findMemberById(id);
    if (existing == null) throw new NotFoundException("成员不存在");
    rejectDirectTemporaryQuota(member);
    // 普通资料接口只能保留已有确认值，明确接受由须知决策接口完成。
    if (
      member.getAcceptedRuleVersion() != null &&
      !java.util.Objects.equals(
        member.getAcceptedRuleVersion(),
        existing.getAcceptedRuleVersion()
      )
    ) {
      throw new WrongFormatException("请通过借阅须知确认接口修改接受版本");
    }
    member.setAcceptedRuleVersion(null);
    member.setBorrowedBookCount(null);
    if (member.getPermanentBorrowLimit() != null) {
      if (member.getPermanentBorrowLimit() < 0) {
        throw new WrongFormatException("长期借阅额度不能小于0");
      }
      if (member.getPermanentBorrowLimit() < existing.getBorrowedBookCount()) {
        throw new ConflictException("最大借阅数量不能低于当前未归还数量");
      }
    }
    if (member.getRoleId() != null) {
      roleQueryService.requireActiveRole(member.getRoleId());
    }
    int rows = memberMapper.updateMember(id, member, expectedVersion);
    if (rows == 0) {
      throw new ConflictException(
        "成员不存在、类型不匹配或信息已被修改，请刷新后重试"
      );
    }
    if (rows != 1) throw new IllegalStateException("成员更新影响了多条记录");
    if (
      member.getAccountStatus() ==
        com.yunfeil161.ailibrary.common.enums.AccountStatus.DISABLED &&
      existing.getAccountStatus() != member.getAccountStatus()
    ) {
      // PUT停用与原逻辑停用一致：撤销全部会话，不能保留旧JWT继续操作。
      loginSessionService.deleteAllForMember(id);
    }
  }

  private void disableMember(
    com.yunfeil161.ailibrary.identity.entity.Member member
  ) {
    if (
      member.getAccountStatus() ==
      com.yunfeil161.ailibrary.common.enums.AccountStatus.DISABLED
    ) {
      throw new ConflictException("成员账号已经停用");
    }
    if (memberMapper.disableMember(member.getId(), member.getVersion()) != 1) {
      throw new ConflictException("成员状态或版本已变化，请刷新后重试");
    }
  }

  private void evictMemberCaches(Long id) {
    cacheClient.deleteRequired(READER_CACHE_KEY_PREFIX + id);
    cacheClient.deleteRequired(EMPLOYEE_CACHE_KEY_PREFIX + id);
  }

  /** 新增的额度筛选必须真正执行SQL，不能被按ID缓存短路。 */
  private boolean canUseIdCache(
    com.yunfeil161.ailibrary.identity.entity.Member member
  ) {
    return (
      member != null &&
      member.getId() != null &&
      member.getPermanentBorrowLimit() == null &&
      member.getTemporaryBorrowLimit() == null &&
      member.getTemporaryQuotaExpiresAt() == null &&
      member.getBorrowedBookCount() == null
    );
  }

  /** 临时额度只由审批事务写入，人员普通新增和更新不能绕过审批。 */
  private void rejectDirectTemporaryQuota(
    com.yunfeil161.ailibrary.identity.entity.Member member
  ) {
    if (
      member.getTemporaryBorrowLimit() != null ||
      member.getTemporaryQuotaExpiresAt() != null
    ) {
      throw new WrongFormatException("临时额度请通过额度申请审批修改");
    }
  }

  /** 旧文本兴趣保持接口兼容，同时建立推荐所需的字典标签关联。 */
  private void replaceInterests(Long memberId, List<String> interests) {
    tagService.replaceLegacyInterests(memberId, interests);
  }

  private Reader findReader(Long id) {
    Reader result = queryReaderById(id);
    if (result == null) {
      throw new NotFoundException("未找到读者信息");
    }
    return result;
  }

  private Reader queryReaderById(Long id) {
    Reader condition = new Reader();
    condition.setId(id);
    return memberMapper
      .getReadersByPage(condition)
      .stream()
      .findFirst()
      .orElse(null);
  }

  private Employee findEmployee(Long id) {
    Employee result = queryEmployeeById(id);
    if (result == null) {
      throw new NotFoundException("未找到员工信息");
    }
    return result;
  }

  private Employee queryEmployeeById(Long id) {
    Employee condition = new Employee();
    condition.setId(id);
    return memberMapper
      .getEmployeesByPage(condition)
      .stream()
      .findFirst()
      .orElse(null);
  }

  private void validateMember(
    com.yunfeil161.ailibrary.identity.entity.Member member,
    MemberType expectedType,
    boolean create
  ) {
    if (
      member == null ||
      expectedType == null ||
      expectedType != member.getMemberType()
    ) {
      throw new WrongFormatException("memberType 与成员类型不匹配");
    }
    if (create && isBlank(member.getName())) {
      throw new WrongFormatException("姓名不能为空");
    }
    if (
      create &&
      (isBlank(member.getEmail()) ||
        isBlank(member.getPhone()) ||
        isBlank(member.getPasswordHash()))
    ) {
      throw new WrongFormatException("邮箱、手机号和密码摘要不能为空");
    }
  }

  private void validateUpdate(
    Long id,
    com.yunfeil161.ailibrary.identity.entity.Member member,
    MemberType expectedType,
    Long expectedVersion
  ) {
    if (id == null || expectedVersion == null || expectedVersion < 0) {
      throw new WrongFormatException(
        "成员 ID 和有效的 expectedVersion 不能为空"
      );
    }
    validateMember(member, expectedType, false);
  }

  private void ensureUnique(
    com.yunfeil161.ailibrary.identity.entity.Member member,
    Long excludeId
  ) {
    if (
      (member.getName() != null ||
        member.getEmail() != null ||
        member.getPhone() != null) &&
      memberMapper.countByIdentity(
        member.getName(),
        member.getEmail(),
        member.getPhone(),
        excludeId
      ) > 0
    ) {
      throw new ConflictException("姓名、邮箱或手机号已存在");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void startPage(Integer page, Integer pageSize) {
    int normalizedPage = page == null || page < 1 ? 1 : page;
    int normalizedSize =
      pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    PageHelper.startPage(normalizedPage, normalizedSize);
  }

  /** 根据成员类型取得默认角色编码，不使用角色固定主键。 */
  private String defaultRoleCode(MemberType memberType) {
    if (memberType == null) throw new WrongFormatException(
      "memberType 不能为空"
    );
    return switch (memberType) {
      case READER -> "READER";
      case EMPLOYEE -> "LIBRARIAN";
      case AI_LIBRARIAN -> throw new WrongFormatException(
        "AI服务身份已退出人员管理"
      );
    };
  }

  /** 管理员是 EMPLOYEE 的一种角色，不再是独立成员类型。 */
  private boolean isAdminRole(Long roleId) {
    return (
      roleId != null &&
      "ADMIN".equals(roleQueryService.getRoleById(roleId).getCode())
    );
  }
}
