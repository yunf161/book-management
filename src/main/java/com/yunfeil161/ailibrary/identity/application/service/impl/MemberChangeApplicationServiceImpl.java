package com.yunfeil161.ailibrary.identity.application.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.config.security.role.service.RoleQueryService;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeCreateRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeDecisionRequest;
import com.yunfeil161.ailibrary.identity.application.dto.MemberChangeQueryRequest;
import com.yunfeil161.ailibrary.identity.application.entity.MemberChangeApplication;
import com.yunfeil161.ailibrary.identity.application.enums.AccountChangeAction;
import com.yunfeil161.ailibrary.identity.application.enums.EmploymentChangeAction;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeDecision;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeStatus;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import com.yunfeil161.ailibrary.identity.application.mapper.MemberChangeApplicationMapper;
import com.yunfeil161.ailibrary.identity.application.service.MemberChangeApplicationService;
import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Member;
import com.yunfeil161.ailibrary.identity.mapper.MemberMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 成员敏感字段申请、权限路由和审批事务实现。 */
@Service
@RequiredArgsConstructor
public class MemberChangeApplicationServiceImpl
  implements MemberChangeApplicationService
{

  private static final String READER_CACHE = "member:reader:";
  private static final String EMPLOYEE_CACHE = "member:employee:";

  private final MemberChangeApplicationMapper applicationMapper;
  private final MemberMapper memberMapper;
  private final com.yunfeil161.ailibrary.policy.service.PolicyState policies;
  private final com.yunfeil161.ailibrary.policy.mapper.PolicyMapper policyMapper;
  private final PermissionAccess permissionAccess;
  private final PermissionQueryService permissionQueryService;
  private final RoleQueryService roleQueryService;
  private final CacheClient cacheClient;
  private final LoginSessionService loginSessionService;
  private final com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications notifications;

  @Override
  @Transactional
  @DistributedLock(
    keys = {
      "'lock:resource:policy-current:ADMISSION'",
      "'lock:resource:policy-current:BORROWING'",
      "'lock:resource:member:' + #currentMemberId",
      "'lock:resource:member-change-application:create:' + #currentMemberId",
    }
  )
  @Audited(resource = AuditResource.MEMBER_CHANGE, id = "")
  public MemberChangeApplication create(MemberChangeCreateRequest request) {
    LoginSession session = permissionAccess.require(
      PermissionCodes.MEMBER_CHANGE_CREATE_SELF,
      DataScope.SELF
    );
    requireHuman(session);
    validateCreateRequest(request, session.getMemberType());

    policies.lockPolicies();
    // 锁住成员行，使“检查重复申请 + 插入申请”对同一成员串行执行。
    if (applicationMapper.lockMember(session.getMemberId()) == null) {
      throw new NotFoundException("成员不存在");
    }
    Member member = requireMember(session.getMemberId());
    if (
      request.getExpectedMemberVersion() != null &&
      !member.getVersion().equals(request.getExpectedMemberVersion())
    ) {
      throw new ConflictException("成员资料已更新，请刷新后重新提交申请");
    }
    if (
      applicationMapper.countPending(
        member.getId(),
        request.getApplicationType()
      ) > 0
    ) {
      throw new ConflictException("同类型申请正在审批中，请勿重复提交");
    }

    MemberChangeApplication application = buildApplication(
      request,
      session,
      member
    );
    prepareDetail(application, request, member);

    // 资格申请提交时立即进入PENDING，阻止重复申请和未审批借阅。
    if (isQualification(application.getApplicationType())) {
      policies.requireAdmission(member, application.getAcceptedRuleVersion());
      var admission = policies.current("ADMISSION");
      var borrowing = policies.current("BORROWING");
      application.setAdmissionPolicyId(admission.getId());
      application.setAdmissionPolicyVersion(admission.getPolicyVersion());
      application.setBorrowingPolicyId(borrowing.getId());
      application.setBorrowingPolicyVersion(borrowing.getPolicyVersion());
      if (
        applicationMapper.updateQualification(
          member.getId(),
          member.getQualificationStatus(),
          QualificationStatus.PENDING,
          application.getAcceptedRuleVersion(),
          member.getVersion()
        ) != 1
      ) {
        throw new ConflictException("资格状态已变化，请刷新后重试");
      }
      application.setExpectedMemberVersion(member.getVersion() + 1);
    }

    if (applicationMapper.insertApplication(application) != 1) {
      throw new IllegalStateException("新增成员变更申请失败");
    }
    insertDetail(application);
    evictMember(member.getId());
    return requireApplication(application.getId());
  }

  @Override
  public PageResult<MemberChangeApplication> queryMine(
    MemberChangeQueryRequest query
  ) {
    LoginSession session = permissionAccess.require(
      PermissionCodes.MEMBER_CHANGE_QUERY_SELF,
      DataScope.SELF
    );
    requireHuman(session);
    MemberChangeQueryRequest normalized =
      query == null ? new MemberChangeQueryRequest() : query;
    // SELF范围永远覆盖客户端传入的申请人和目标成员，避免横向越权。
    normalized.setApplicantMemberId(session.getMemberId());
    normalized.setTargetMemberId(session.getMemberId());
    return query(normalized, false);
  }

  @Override
  public PageResult<MemberChangeApplication> queryForReview(
    MemberChangeQueryRequest query
  ) {
    LoginSession session = currentSession();
    boolean employeeAccess = hasAllScope(
      session.getRoleId(),
      PermissionCodes.MEMBER_CHANGE_QUERY_EMPLOYEE
    );
    boolean readerAccess = hasAllScope(
      session.getRoleId(),
      PermissionCodes.MEMBER_CHANGE_QUERY_READER
    );
    if (!employeeAccess && !readerAccess) {
      throw new ForbiddenException("没有变更申请查询权限");
    }
    MemberChangeQueryRequest normalized =
      query == null ? new MemberChangeQueryRequest() : query;
    validateQuery(normalized);
    if (
      normalized.getApplicationType() != null &&
      normalized.getApplicationType().isEmployeeRequest() &&
      !employeeAccess
    ) {
      throw new ForbiddenException("没有员工或管理员申请查询权限");
    }
    return query(normalized, !employeeAccess);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "@memberChangeLockKeys.forApplication(#id)")
  @Audited(resource = AuditResource.MEMBER_CHANGE, id = "#id")
  public MemberChangeApplication cancel(Long id, Long expectedVersion) {
    LoginSession session = permissionAccess.require(
      PermissionCodes.MEMBER_CHANGE_CANCEL_SELF,
      DataScope.SELF
    );
    requireIdAndVersion(id, expectedVersion);
    MemberChangeApplication application = requireApplicationForUpdate(id);
    if (!session.getMemberId().equals(application.getApplicantMemberId())) {
      throw new ForbiddenException("只能取消本人申请");
    }
    if (application.getStatus() != MemberChangeStatus.PENDING) {
      throw new ConflictException("只有待审批申请可以取消");
    }
    requireNotExpired(application);

    if (isQualification(application.getApplicationType())) {
      restoreQualification(application);
    }
    if (
      applicationMapper.cancel(
        id,
        session.getMemberId(),
        expectedVersion,
        LocalDateTime.now()
      ) != 1
    ) {
      throw new ConflictException("申请状态或版本已变化，请刷新后重试");
    }
    evictMember(application.getTargetMemberId());
    return requireApplication(id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "@memberChangeLockKeys.forApplication(#id)")
  @Audited(resource = AuditResource.MEMBER_CHANGE, id = "#id")
  public MemberChangeApplication decide(
    Long id,
    MemberChangeDecisionRequest request
  ) {
    // 必须先鉴权，避免无权限调用者通过400响应探测审批参数规则。
    LoginSession reviewer = requireReviewEntry();
    policies.lockPolicies();
    if (request == null || request.getDecision() == null) {
      throw new WrongFormatException("审批决定不能为空");
    }
    if (request.getReason() != null && request.getReason().length() > 500) {
      throw new WrongFormatException("审批说明不能超过500个字符");
    }
    if (
      request.getDecision() == MemberChangeDecision.REJECT &&
      isBlank(request.getReason())
    ) {
      throw new WrongFormatException("拒绝申请必须填写原因");
    }
    requireIdAndVersion(id, request.getExpectedVersion());

    // 先要求至少一种审核查询权限，防止无权限用户枚举申请ID和类型。
    MemberChangeApplication application = requireApplicationForUpdate(id);
    requireApprovalPermission(application.getApplicationType());
    if (reviewer.getMemberId().equals(application.getApplicantMemberId())) {
      throw new ForbiddenException("不能审批自己提交的申请");
    }
    if (application.getStatus() != MemberChangeStatus.PENDING) {
      throw new ConflictException("申请已经处理，不能重复审批");
    }
    // 定时扫描前也不能处理已到期的申请；最终SQL再次检查到期条件。
    requireNotExpired(application);

    if (request.getDecision() == MemberChangeDecision.APPROVE) {
      applyApprovedChange(application);
    } else if (isQualification(application.getApplicationType())) {
      rejectQualification(application);
    }

    MemberChangeStatus resultStatus =
      request.getDecision() == MemberChangeDecision.APPROVE
        ? MemberChangeStatus.APPROVED
        : MemberChangeStatus.REJECTED;
    if (
      applicationMapper.decide(
        id,
        request.getDecision(),
        resultStatus,
        request.getReason(),
        reviewer.getMemberId(),
        reviewer.getRoleId(),
        request.getExpectedVersion(),
        // 与Java到期判断共用业务时钟，避免数据库容器时区影响截止判断。
        LocalDateTime.now()
      ) != 1
    ) {
      throw new ConflictException("申请状态或版本已变化，请刷新后重试");
    }
    evictMember(application.getTargetMemberId());
    notifications.memberDecision(requireApplication(id));
    return requireApplication(id);
  }

  private MemberChangeApplication buildApplication(
    MemberChangeCreateRequest request,
    LoginSession session,
    Member member
  ) {
    MemberChangeApplication value = new MemberChangeApplication();
    value.setApplicationNo(
      "MCA" +
        UUID.randomUUID()
          .toString()
          .replace("-", "")
          .substring(0, 20)
          .toUpperCase()
    );
    value.setApplicantMemberId(session.getMemberId());
    value.setTargetMemberId(session.getMemberId());
    value.setApplicantMemberType(session.getMemberType());
    value.setApplicantRoleId(session.getRoleId());
    value.setApplicationType(request.getApplicationType());
    value.setStatus(MemberChangeStatus.PENDING);
    value.setReason(request.getReason().trim());
    // 新申请由服务器记录锁内快照，审批时仍以该快照进行乐观锁校验。
    value.setExpectedMemberVersion(member.getVersion());
    value.setExpiresAt(LocalDateTime.now().plusDays(7));
    value.setCurrentQualificationStatus(member.getQualificationStatus());
    value.setCurrentAcceptedRuleVersion(member.getAcceptedRuleVersion());
    // 申请快照记录提交时真正生效的总额度，可能是临时额度。
    value.setCurrentMaxBorrowCount(member.getEffectiveBorrowLimit());
    value.setCurrentAccountStatus(member.getAccountStatus());
    value.setCurrentRoleId(member.getRoleId());
    return value;
  }

  private void prepareDetail(
    MemberChangeApplication application,
    MemberChangeCreateRequest request,
    Member member
  ) {
    MemberChangeType type = application.getApplicationType();
    switch (type) {
      case READER_QUALIFICATION -> {
        if (
          member.getQualificationStatus() != QualificationStatus.NOT_APPLIED &&
          member.getQualificationStatus() != QualificationStatus.REJECTED
        ) {
          throw new ConflictException("当前资格状态不能重新申请");
        }
        application.setAcceptedRuleVersion(
          request.getAcceptedRuleVersion().trim()
        );
      }
      case READER_QUALIFICATION_RESTORE -> {
        if (member.getQualificationStatus() != QualificationStatus.SUSPENDED) {
          throw new ConflictException("只有已暂停的资格可以申请恢复");
        }
        application.setAcceptedRuleVersion(member.getAcceptedRuleVersion());
      }
      case BORROW_QUOTA_CHANGE -> {
        int requested = request.getRequestedMaxBorrowCount();
        if (requested < member.getBorrowedBookCount()) {
          throw new ConflictException("申请额度不能低于当前未归还数量");
        }
        if (
          request.getQuotaDurationDays() == null &&
          requested == member.getPermanentBorrowLimit() &&
          member.getTemporaryBorrowLimit() == null
        ) {
          throw new ConflictException("申请额度与当前额度相同，无需变更");
        }
        application.setRequestedMaxBorrowCount(requested);
        application.setQuotaDurationDays(request.getQuotaDurationDays());
      }
      case ACCOUNT_REACTIVATION -> {
        if (member.getAccountStatus() != AccountStatus.DISABLED) {
          throw new ConflictException("只有已停用账号可以申请恢复");
        }
        application.setRequestedAccountAction(AccountChangeAction.REACTIVATE);
      }
      default -> prepareEmployeeDetail(application, request, member);
    }
  }

  private void prepareEmployeeDetail(
    MemberChangeApplication application,
    MemberChangeCreateRequest request,
    Member member
  ) {
    if (member.getMemberType() != MemberType.EMPLOYEE) {
      throw new ForbiddenException("只有员工或管理员可以提交人事变更申请");
    }
    Employee employee = memberMapper
      .getEmployeesByPage(employeeCondition(member.getId()))
      .stream()
      .findFirst()
      .orElseThrow(() -> new NotFoundException("员工档案不存在"));
    Long profileVersion = applicationMapper.findEmployeeProfileVersion(
      member.getId()
    );
    if (
      request.getExpectedProfileVersion() != null &&
      !request.getExpectedProfileVersion().equals(profileVersion)
    ) {
      throw new ConflictException("员工档案已更新，请刷新后重新提交申请");
    }
    application.setExpectedProfileVersion(profileVersion);
    application.setCurrentDepartmentId(employee.getDepartmentId());
    application.setCurrentPositionName(employee.getPositionName());
    application.setCurrentEmploymentStatus(employee.getEmploymentStatus());
    application.setCurrentHiredOn(employee.getHiredOn());
    application.setCurrentLeftOn(employee.getLeftOn());
    application.setRequestedDepartmentId(request.getRequestedDepartmentId());
    application.setRequestedPositionName(
      trimToNull(request.getRequestedPositionName())
    );
    application.setRequestedRoleId(request.getRequestedRoleId());
    application.setRequestedEmploymentAction(
      request.getRequestedEmploymentAction()
    );

    MemberChangeType type = application.getApplicationType();
    EmploymentStatus currentStatus = employee.getEmploymentStatus();
    if (type == MemberChangeType.EMPLOYEE_REINSTATEMENT) {
      if (
        currentStatus != EmploymentStatus.SUSPENDED &&
        currentStatus != EmploymentStatus.LEFT
      ) {
        throw new ConflictException("只有停职或离职员工可以申请复职");
      }
    } else if (currentStatus != EmploymentStatus.ACTIVE) {
      throw new ConflictException("只有在职员工可以提交该人事变更申请");
    }

    if (
      (type == MemberChangeType.EMPLOYEE_PROFILE_CHANGE ||
        type == MemberChangeType.EMPLOYEE_TRANSFER) &&
      request.getRequestedDepartmentId() != null &&
      applicationMapper.countDepartment(request.getRequestedDepartmentId()) != 1
    ) {
      throw new ConflictException("目标部门不存在或已停用");
    }
    if (
      type == MemberChangeType.EMPLOYEE_REINSTATEMENT ||
      type == MemberChangeType.EMPLOYEE_ROLE_CHANGE
    ) {
      requireEmployeeRole(request.getRequestedRoleId());
    }
    if (
      (type == MemberChangeType.EMPLOYEE_PROFILE_CHANGE ||
        type == MemberChangeType.EMPLOYEE_TRANSFER) &&
      (request.getRequestedDepartmentId() == null ||
        request
          .getRequestedDepartmentId()
          .equals(employee.getDepartmentId())) &&
      (trimToNull(request.getRequestedPositionName()) == null ||
        trimToNull(request.getRequestedPositionName()).equals(
          employee.getPositionName()
        ))
    ) {
      throw new ConflictException("部门和岗位均未发生变化");
    }
    if (
      type == MemberChangeType.EMPLOYEE_ROLE_CHANGE &&
      request.getRequestedRoleId().equals(member.getRoleId())
    ) {
      throw new ConflictException("目标角色与当前角色相同，无需变更");
    }
  }

  private void insertDetail(MemberChangeApplication application) {
    /*
     * 账号恢复可能由读者或员工提出，按申请人的成员类型写入对应明细表。
     * 其他申请则按申请类型决定明细表，确保聚合查询时字段来源明确。
     */
    boolean readerDetail =
      application.getApplicationType().isReaderRequest() ||
      (application.getApplicationType() ==
        MemberChangeType.ACCOUNT_REACTIVATION &&
        application.getApplicantMemberType() == MemberType.READER);
    int rows = readerDetail
      ? applicationMapper.insertReaderDetail(application)
      : applicationMapper.insertEmployeeDetail(application);
    if (rows != 1) throw new IllegalStateException("新增申请明细失败");
  }

  private void applyApprovedChange(MemberChangeApplication application) {
    Member current = requireMember(application.getTargetMemberId());
    if (!current.getVersion().equals(application.getExpectedMemberVersion())) {
      throw new ConflictException("成员资料已变化，原申请不能继续执行");
    }

    if (isQualification(application.getApplicationType())) {
      policies.requireAdmission(current, application.getAcceptedRuleVersion());
      var admission = policies.current("ADMISSION");
      if (
        !java.util.Objects.equals(
          admission.getId(),
          application.getAdmissionPolicyId()
        ) ||
        !java.util.Objects.equals(
          admission.getPolicyVersion(),
          application.getAdmissionPolicyVersion()
        )
      ) throw new ConflictException("准入政策已更新，请重新申请");
      policies.verifyBorrowSnapshot(
        application.getBorrowingPolicyId(),
        application.getBorrowingPolicyVersion()
      );
    }
    int rows = switch (application.getApplicationType()) {
      case
        READER_QUALIFICATION,
        READER_QUALIFICATION_RESTORE -> applicationMapper.updateQualification(
        current.getId(),
        QualificationStatus.PENDING,
        QualificationStatus.APPROVED,
        application.getAcceptedRuleVersion(),
        current.getVersion()
      );
      case BORROW_QUOTA_CHANGE -> applicationMapper.updateQuota(
        current.getId(),
        application.getRequestedMaxBorrowCount(),
        // 有效期从审批通过开始计时，不从申请提交时间开始。
        application.getQuotaDurationDays() == null
          ? null
          : LocalDateTime.now().plusDays(application.getQuotaDurationDays()),
        current.getVersion()
      );
      case ACCOUNT_REACTIVATION -> applicationMapper.updateAccountStatus(
        current.getId(),
        AccountStatus.DISABLED,
        AccountStatus.ACTIVE,
        current.getVersion()
      );
      case EMPLOYEE_PROFILE_CHANGE, EMPLOYEE_TRANSFER -> applyEmployeeProfile(
        application
      );
      case EMPLOYEE_SUSPENSION -> applyEmploymentAndRevokeRole(
        application,
        EmploymentStatus.ACTIVE,
        EmploymentStatus.SUSPENDED,
        null
      );
      case EMPLOYEE_RESIGNATION -> applyEmploymentAndRevokeRole(
        application,
        EmploymentStatus.ACTIVE,
        EmploymentStatus.LEFT,
        LocalDate.now()
      );
      case EMPLOYEE_REINSTATEMENT -> applyReinstatement(application, current);
      case EMPLOYEE_ROLE_CHANGE -> {
        requireEmployeeRole(application.getRequestedRoleId());
        yield applicationMapper.updateRole(
          current.getId(),
          application.getRequestedRoleId(),
          current.getVersion()
        );
      }
    };
    if (
      rows == 1 &&
      application.getApplicationType() == MemberChangeType.READER_QUALIFICATION
    ) {
      // 首次开通使用默认额度，恢复资格保留既有额度。
      if (
        current.getBorrowedBookCount() > policies.defaultQuota()
      ) throw new ConflictException("默认额度低于已借数量");
      if (
        policyMapper.initialQuota(current.getId(), policies.defaultQuota()) != 1
      ) throw new IllegalStateException("设置初始额度失败");
    }
    if (rows != 1) {
      throw new ConflictException("目标资料已变化或申请条件已不再满足");
    }
    if (
      application.getApplicationType() ==
        MemberChangeType.ACCOUNT_REACTIVATION ||
      application.getApplicationType() ==
        MemberChangeType.EMPLOYEE_SUSPENSION ||
      application.getApplicationType() ==
        MemberChangeType.EMPLOYEE_RESIGNATION ||
      application.getApplicationType() ==
        MemberChangeType.EMPLOYEE_REINSTATEMENT ||
      application.getApplicationType() == MemberChangeType.EMPLOYEE_ROLE_CHANGE
    ) {
      loginSessionService.deleteAllForMember(application.getTargetMemberId());
    }
  }

  private int applyEmployeeProfile(MemberChangeApplication application) {
    if (
      application.getRequestedDepartmentId() != null &&
      applicationMapper.countDepartment(
        application.getRequestedDepartmentId()
      ) != 1
    ) {
      throw new ConflictException("目标部门不存在或已停用");
    }
    return applicationMapper.updateEmployeeProfile(
      application.getTargetMemberId(),
      application.getRequestedDepartmentId(),
      application.getRequestedPositionName(),
      application.getExpectedProfileVersion()
    );
  }

  private int applyEmployment(
    MemberChangeApplication application,
    EmploymentStatus from,
    EmploymentStatus to,
    LocalDate leftOn
  ) {
    return applicationMapper.updateEmployment(
      application.getTargetMemberId(),
      from,
      to,
      leftOn,
      application.getExpectedProfileVersion()
    );
  }

  /** 停职或离职同时撤销员工角色，避免重新登录后继续获得原岗位权限。 */
  private int applyEmploymentAndRevokeRole(
    MemberChangeApplication application,
    EmploymentStatus from,
    EmploymentStatus to,
    LocalDate leftOn
  ) {
    int profileRows = applyEmployment(application, from, to, leftOn);
    if (profileRows != 1) return profileRows;
    Long readerRoleId = roleQueryService.getRoleByCode("READER").getId();
    return applicationMapper.updateRole(
      application.getTargetMemberId(),
      readerRoleId,
      application.getExpectedMemberVersion()
    );
  }

  /** 复职时由管理员同时确认恢复后的有效员工角色。 */
  private int applyReinstatement(
    MemberChangeApplication application,
    Member current
  ) {
    requireEmployeeRole(application.getRequestedRoleId());
    int profileRows = applyEmployment(
      application,
      application.getCurrentEmploymentStatus(),
      EmploymentStatus.ACTIVE,
      null
    );
    if (profileRows != 1) return profileRows;
    return applicationMapper.updateRole(
      current.getId(),
      application.getRequestedRoleId(),
      current.getVersion()
    );
  }

  private void rejectQualification(MemberChangeApplication application) {
    QualificationStatus target =
      application.getApplicationType() == MemberChangeType.READER_QUALIFICATION
        ? QualificationStatus.REJECTED
        : QualificationStatus.SUSPENDED;
    if (
      applicationMapper.updateQualification(
        application.getTargetMemberId(),
        QualificationStatus.PENDING,
        target,
        application.getCurrentAcceptedRuleVersion(),
        application.getExpectedMemberVersion()
      ) != 1
    ) {
      throw new ConflictException("资格状态已变化，不能执行原审批");
    }
  }

  private void restoreQualification(MemberChangeApplication application) {
    if (
      applicationMapper.updateQualification(
        application.getTargetMemberId(),
        QualificationStatus.PENDING,
        application.getCurrentQualificationStatus(),
        application.getCurrentAcceptedRuleVersion(),
        application.getExpectedMemberVersion()
      ) != 1
    ) {
      throw new ConflictException("资格状态已变化，不能取消原申请");
    }
  }

  private void requireApprovalPermission(MemberChangeType type) {
    String code = switch (type) {
      case
        READER_QUALIFICATION,
        READER_QUALIFICATION_RESTORE -> PermissionCodes.READER_QUALIFICATION_APPROVE;
      case BORROW_QUOTA_CHANGE -> PermissionCodes.BORROW_QUOTA_CHANGE_APPROVE;
      case ACCOUNT_REACTIVATION -> PermissionCodes.MEMBER_ACCOUNT_CHANGE_APPROVE;
      case
        EMPLOYEE_PROFILE_CHANGE,
        EMPLOYEE_TRANSFER -> PermissionCodes.EMPLOYEE_PROFILE_CHANGE_APPROVE;
      case
        EMPLOYEE_SUSPENSION,
        EMPLOYEE_RESIGNATION,
        EMPLOYEE_REINSTATEMENT -> PermissionCodes.EMPLOYEE_STATUS_CHANGE_APPROVE;
      case EMPLOYEE_ROLE_CHANGE -> PermissionCodes.EMPLOYEE_ROLE_CHANGE_APPROVE;
    };
    permissionAccess.require(code, DataScope.ALL);
  }

  private LoginSession requireReviewEntry() {
    LoginSession session = currentSession();
    boolean allowed =
      hasAllScope(
        session.getRoleId(),
        PermissionCodes.MEMBER_CHANGE_QUERY_READER
      ) ||
      hasAllScope(
        session.getRoleId(),
        PermissionCodes.MEMBER_CHANGE_QUERY_EMPLOYEE
      );
    if (!allowed) throw new ForbiddenException("没有变更申请审核权限");
    return session;
  }

  private LoginSession currentSession() {
    LoginSession session = LoginUserContext.get();
    if (session == null || session.getRoleId() == null) {
      throw new com.yunfeil161.ailibrary.common.exception.UnauthorizedException(
        "未登录或凭证失效"
      );
    }
    requireHuman(session);
    return session;
  }

  private PageResult<MemberChangeApplication> query(
    MemberChangeQueryRequest query,
    boolean readerOnly
  ) {
    validateQuery(query);
    int page =
      query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
    int size =
      query.getPageSize() == null || query.getPageSize() < 1
        ? 10
        : Math.min(query.getPageSize(), 100);
    PageHelper.startPage(page, size);
    PageInfo<MemberChangeApplication> info = new PageInfo<>(
      applicationMapper.query(query, readerOnly)
    );
    return new PageResult<>(info.getTotal(), info.getList());
  }

  private void validateCreateRequest(
    MemberChangeCreateRequest request,
    MemberType memberType
  ) {
    if (
      request == null ||
      request.getApplicationType() == null ||
      (request.getExpectedMemberVersion() != null &&
        request.getExpectedMemberVersion() < 0) ||
      isBlank(request.getReason())
    ) {
      throw new WrongFormatException(
        "申请类型和原因不能为空，资料版本不能小于0"
      );
    }
    if (request.getReason().length() > 500) {
      throw new WrongFormatException("申请原因不能超过500个字符");
    }
    MemberChangeType type = request.getApplicationType();
    if (
      type.isEmployeeRequest() &&
      type != MemberChangeType.ACCOUNT_REACTIVATION &&
      memberType != MemberType.EMPLOYEE
    ) {
      throw new ForbiddenException("只有员工或管理员可以提交该申请");
    }
    switch (type) {
      case READER_QUALIFICATION -> require(
        !isBlank(request.getAcceptedRuleVersion()),
        "申请借阅资格必须接受当前规则"
      );
      case READER_QUALIFICATION_RESTORE -> {
      }
      case BORROW_QUOTA_CHANGE -> require(
        request.getRequestedMaxBorrowCount() != null &&
          request.getRequestedMaxBorrowCount() >= 0 &&
          (request.getQuotaDurationDays() == null ||
            (request.getQuotaDurationDays() > 0 &&
              request.getQuotaDurationDays() <= 3650)),
        "借阅额度和有效天数格式错误"
      );
      case ACCOUNT_REACTIVATION -> require(
        request.getRequestedAccountAction() == AccountChangeAction.REACTIVATE,
        "账号恢复动作不正确"
      );
      case EMPLOYEE_PROFILE_CHANGE, EMPLOYEE_TRANSFER -> require(
        request.getRequestedDepartmentId() != null ||
          !isBlank(request.getRequestedPositionName()),
        "部门和岗位至少修改一项"
      );
      case EMPLOYEE_SUSPENSION -> require(
        request.getRequestedEmploymentAction() ==
          EmploymentChangeAction.SUSPEND,
        "停职申请动作不正确"
      );
      case EMPLOYEE_RESIGNATION -> require(
        request.getRequestedEmploymentAction() == EmploymentChangeAction.RESIGN,
        "离职申请动作不正确"
      );
      case EMPLOYEE_REINSTATEMENT -> require(
        request.getRequestedEmploymentAction() ==
          EmploymentChangeAction.REINSTATE &&
          request.getRequestedRoleId() != null &&
          request.getRequestedRoleId() > 0,
        "复职申请动作和目标角色不能为空"
      );
      case EMPLOYEE_ROLE_CHANGE -> require(
        request.getRequestedRoleId() != null &&
          request.getRequestedRoleId() > 0,
        "目标角色不能为空"
      );
    }
    if (
      request.getAcceptedRuleVersion() != null &&
      request.getAcceptedRuleVersion().length() > 64
    ) {
      throw new WrongFormatException("规则版本不能超过64个字符");
    }
    if (
      request.getRequestedPositionName() != null &&
      request.getRequestedPositionName().trim().length() > 80
    ) {
      throw new WrongFormatException("岗位名称不能超过80个字符");
    }
    if (
      request.getRequestedDepartmentId() != null &&
      request.getRequestedDepartmentId() <= 0
    ) {
      throw new WrongFormatException("目标部门ID必须大于0");
    }
    if (
      request.getExpectedProfileVersion() != null &&
      request.getExpectedProfileVersion() < 0
    ) {
      throw new WrongFormatException("员工档案版本不能小于0");
    }
    rejectUnexpectedFields(request);
  }

  /** 管理端聚合查询必须拥有ALL范围，SELF权限不能被当成后台全量查询权限。 */
  private boolean hasAllScope(Long roleId, String permissionCode) {
    return permissionQueryService
      .findDataScope(roleId, permissionCode)
      .filter(scope -> scope == DataScope.ALL)
      .isPresent();
  }

  private void validateQuery(MemberChangeQueryRequest query) {
    if (
      (query.getId() != null && query.getId() <= 0) ||
      (query.getApplicantMemberId() != null &&
        query.getApplicantMemberId() <= 0) ||
      (query.getTargetMemberId() != null && query.getTargetMemberId() <= 0) ||
      (query.getReviewerMemberId() != null && query.getReviewerMemberId() <= 0)
    ) {
      throw new WrongFormatException("查询ID必须大于0");
    }
    if (
      query.getCreatedFrom() != null &&
      query.getCreatedTo() != null &&
      query.getCreatedFrom().isAfter(query.getCreatedTo())
    ) {
      throw new WrongFormatException("查询开始时间不能晚于结束时间");
    }
  }

  /**
   * 每种申请只接受自己的字段，避免聚合DTO成为绕过业务边界的万能更新入口。
   */
  private void rejectUnexpectedFields(MemberChangeCreateRequest request) {
    MemberChangeType type = request.getApplicationType();
    boolean valid = switch (type) {
      case READER_QUALIFICATION -> request.getRequestedMaxBorrowCount() ==
        null &&
        request.getQuotaDurationDays() == null &&
        noEmployeePayload(request) &&
        request.getRequestedAccountAction() == null;
      case READER_QUALIFICATION_RESTORE -> request.getAcceptedRuleVersion() ==
        null &&
        request.getRequestedMaxBorrowCount() == null &&
        request.getQuotaDurationDays() == null &&
        noEmployeePayload(request) &&
        request.getRequestedAccountAction() == null;
      case BORROW_QUOTA_CHANGE -> request.getAcceptedRuleVersion() == null &&
        noEmployeePayload(request) &&
        request.getRequestedAccountAction() == null;
      case ACCOUNT_REACTIVATION -> request.getAcceptedRuleVersion() == null &&
        request.getRequestedMaxBorrowCount() == null &&
        request.getQuotaDurationDays() == null &&
        noEmployeePayload(request);
      case EMPLOYEE_PROFILE_CHANGE -> noReaderPayload(request) &&
        request.getRequestedEmploymentAction() == null &&
        request.getRequestedRoleId() == null &&
        request.getRequestedAccountAction() == null;
      case EMPLOYEE_TRANSFER -> noReaderPayload(request) &&
        request.getRequestedEmploymentAction() ==
          EmploymentChangeAction.TRANSFER &&
        request.getRequestedRoleId() == null &&
        request.getRequestedAccountAction() == null;
      case EMPLOYEE_SUSPENSION, EMPLOYEE_RESIGNATION -> noReaderPayload(
        request
      ) &&
        request.getRequestedDepartmentId() == null &&
        request.getRequestedPositionName() == null &&
        request.getRequestedRoleId() == null &&
        request.getRequestedAccountAction() == null;
      case EMPLOYEE_REINSTATEMENT -> noReaderPayload(request) &&
        request.getRequestedDepartmentId() == null &&
        request.getRequestedPositionName() == null &&
        request.getRequestedRoleId() != null &&
        request.getRequestedAccountAction() == null;
      case EMPLOYEE_ROLE_CHANGE -> noReaderPayload(request) &&
        request.getRequestedDepartmentId() == null &&
        request.getRequestedPositionName() == null &&
        request.getRequestedEmploymentAction() == null &&
        request.getRequestedAccountAction() == null;
    };
    if (!valid) {
      throw new WrongFormatException("申请包含当前类型不允许修改的字段");
    }
  }

  private boolean noReaderPayload(MemberChangeCreateRequest request) {
    return (
      request.getAcceptedRuleVersion() == null &&
      request.getRequestedMaxBorrowCount() == null &&
      request.getQuotaDurationDays() == null
    );
  }

  private boolean noEmployeePayload(MemberChangeCreateRequest request) {
    return (
      request.getRequestedDepartmentId() == null &&
      request.getRequestedPositionName() == null &&
      request.getRequestedEmploymentAction() == null &&
      request.getRequestedRoleId() == null
    );
  }

  private Member requireMember(Long id) {
    Member member = memberMapper.findMemberById(id);
    if (member == null) throw new NotFoundException("成员不存在");
    return member;
  }

  private MemberChangeApplication requireApplication(Long id) {
    MemberChangeApplication value = applicationMapper.findById(id);
    if (value == null) throw new NotFoundException("成员变更申请不存在");
    return value;
  }

  private MemberChangeApplication requireApplicationForUpdate(Long id) {
    MemberChangeApplication value = applicationMapper.findByIdForUpdate(id);
    if (value == null) throw new NotFoundException("成员变更申请不存在");
    return value;
  }

  private Employee employeeCondition(Long id) {
    Employee value = new Employee();
    value.setId(id);
    return value;
  }

  private void requireHuman(LoginSession session) {
    if (session.getMemberType() == MemberType.AI_LIBRARIAN) {
      throw new ForbiddenException("AI馆员不能使用成员变更申请");
    }
  }

  private void requireEmployeeRole(Long roleId) {
    String code = roleQueryService.requireActiveRole(roleId).getCode();
    if (!"LIBRARIAN".equals(code) && !"ADMIN".equals(code)) {
      throw new WrongFormatException("员工只能申请馆员或管理员角色");
    }
  }

  private boolean isQualification(MemberChangeType type) {
    return (
      type == MemberChangeType.READER_QUALIFICATION ||
      type == MemberChangeType.READER_QUALIFICATION_RESTORE
    );
  }

  private void requireNotExpired(MemberChangeApplication application) {
    if (
      application.getExpiresAt() != null &&
      !application.getExpiresAt().isAfter(LocalDateTime.now())
    ) {
      throw new ConflictException("申请已过期，不能继续处理");
    }
  }

  private void requireIdAndVersion(Long id, Long version) {
    if (id == null || id <= 0 || version == null || version < 0) {
      throw new WrongFormatException("申请ID和有效的expectedVersion不能为空");
    }
  }

  private void require(boolean condition, String message) {
    if (!condition) throw new WrongFormatException(message);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String trimToNull(String value) {
    return isBlank(value) ? null : value.trim();
  }

  private void evictMember(Long memberId) {
    cacheClient.deleteRequired(READER_CACHE + memberId);
    cacheClient.deleteRequired(EMPLOYEE_CACHE + memberId);
  }
}
