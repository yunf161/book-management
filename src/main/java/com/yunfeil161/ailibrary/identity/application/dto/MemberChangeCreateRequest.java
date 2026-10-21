package com.yunfeil161.ailibrary.identity.application.dto;

import com.yunfeil161.ailibrary.identity.application.enums.AccountChangeAction;
import com.yunfeil161.ailibrary.identity.application.enums.EmploymentChangeAction;
import com.yunfeil161.ailibrary.identity.application.enums.MemberChangeType;
import lombok.Data;

/** 提交成员敏感字段变更申请的统一请求。 */
@Data
public class MemberChangeCreateRequest {

  private MemberChangeType applicationType;
  private String reason;
  /** 兼容旧客户端的可选快照版本；不传时由服务器在成员锁内自动记录。 */
  private Long expectedMemberVersion;
  /** 员工档案快照版本同样由服务器自动记录，显式传入时仍验证旧版本冲突。 */
  private Long expectedProfileVersion;

  private String acceptedRuleVersion;
  /** 申请的借阅总额度，不是叠加数量；保留既有申请字段名称。 */
  private Integer requestedMaxBorrowCount;
  /** 留空为长期调整；1～3650天为临时调整，从审批通过开始计时。 */
  private Integer quotaDurationDays;

  private Long requestedDepartmentId;
  private String requestedPositionName;
  private EmploymentChangeAction requestedEmploymentAction;
  private Long requestedRoleId;
  private AccountChangeAction requestedAccountAction;
}
