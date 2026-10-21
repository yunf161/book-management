package com.yunfeil161.ailibrary.identity.application.entity;

import com.yunfeil161.ailibrary.common.enums.AccountStatus;
import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.enums.QualificationStatus;
import com.yunfeil161.ailibrary.identity.application.enums.AccountChangeAction;
import com.yunfeil161.ailibrary.identity.application.enums.EmploymentChangeAction;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeDecision;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeStatus;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 聚合成员变更申请。
 *
 * <p>主表字段与读者、员工明细在查询时合并，便于一个接口返回不同类型申请；
 * Service仍按applicationType执行严格字段白名单。</p>
 */
@Data
public class MemberChangeApplication {
    /** 提交时生效政策快照；旧申请没有快照时不允许审批通过。 */
    private Long admissionPolicyId;
    private Long admissionPolicyVersion;
    private Long borrowingPolicyId;
    private Long borrowingPolicyVersion;

  private Long id;
  private String applicationNo;
  private Long applicantMemberId;
  private Long targetMemberId;
  private MemberType applicantMemberType;
  private Long applicantRoleId;
  private MemberChangeType applicationType;
  private MemberChangeStatus status;
  private String reason;
  private Long expectedMemberVersion;
  private Long expectedProfileVersion;
  private Long reviewerMemberId;
  private Long reviewerRoleId;
  private MemberChangeDecision decision;
  private String decisionReason;
  private LocalDateTime decidedAt;
  private LocalDateTime cancelledAt;
  private LocalDateTime expiresAt;
  private Long version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  private QualificationStatus currentQualificationStatus;
  /** 提交申请前已经接受的规则版本，用于拒绝或取消时完整恢复成员状态。 */
  private String currentAcceptedRuleVersion;
  private String acceptedRuleVersion;
  /** 提交时的生效总额度快照，可能来自长期或临时额度。 */
  private Integer currentMaxBorrowCount;
  private Integer requestedMaxBorrowCount;
  /** 临时有效天数；空值表示审批修改长期额度并结束原临时额度。 */
  private Integer quotaDurationDays;
  private AccountStatus currentAccountStatus;
  private AccountChangeAction requestedAccountAction;

  private Long currentDepartmentId;
  private Long requestedDepartmentId;
  private String currentPositionName;
  private String requestedPositionName;
  private EmploymentStatus currentEmploymentStatus;
  private EmploymentChangeAction requestedEmploymentAction;
  private Long currentRoleId;
  private Long requestedRoleId;
  private LocalDate currentHiredOn;
  private LocalDate currentLeftOn;
}
