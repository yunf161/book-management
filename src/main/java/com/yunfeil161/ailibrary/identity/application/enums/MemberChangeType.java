package com.yunfeil161.ailibrary.identity.application.enums;

/** 成员可以提交的受审批变更类型。 */
public enum MemberChangeType {
  READER_QUALIFICATION,
  READER_QUALIFICATION_RESTORE,
  BORROW_QUOTA_CHANGE,
  ACCOUNT_REACTIVATION,
  EMPLOYEE_PROFILE_CHANGE,
  EMPLOYEE_TRANSFER,
  EMPLOYEE_SUSPENSION,
  EMPLOYEE_RESIGNATION,
  EMPLOYEE_REINSTATEMENT,
  EMPLOYEE_ROLE_CHANGE;

  /** 读者资格、额度和账号恢复申请可以由馆员处理。 */
  public boolean isReaderRequest() {
    return this == READER_QUALIFICATION ||
      this == READER_QUALIFICATION_RESTORE ||
      this == BORROW_QUOTA_CHANGE;
  }

  /** 员工和管理员的人事、角色申请只能由有权限的管理员处理。 */
  public boolean isEmployeeRequest() {
    return this == EMPLOYEE_PROFILE_CHANGE ||
      this == EMPLOYEE_TRANSFER ||
      this == EMPLOYEE_SUSPENSION ||
      this == EMPLOYEE_RESIGNATION ||
      this == EMPLOYEE_REINSTATEMENT ||
      this == EMPLOYEE_ROLE_CHANGE ||
      this == ACCOUNT_REACTIVATION;
  }
}
