package com.yunfeil161.ailibrary.config.security.permission;

/**
 * 系统权限编码常量，避免各业务模块直接散落字符串字面量。
 */
public final class PermissionCodes {
  /** AI只读咨询入口；每个业务工具仍检查其自身权限。 */
  public static final String AI_CHAT_SELF = "ai:chat:self";
  public static final String AI_BUSINESS_SELF = "ai:business:self";

  /** 规则推荐和兴趣修改都限定当前登录成员。 */
  public static final String RECOMMENDATION_QUERY = "recommendation:query:self";
  public static final String RECOMMENDATION_REFRESH =
    "recommendation:refresh:self";
  public static final String RECOMMENDATION_FEEDBACK =
    "recommendation:feedback:self";
  public static final String INTERESTS_UPDATE = "interests:update:self";

  /** 政策版本管理只按角色显式授权。 */
  public static final String POLICY_QUERY = "policy:query";
  public static final String POLICY_CREATE = "policy:create";
  public static final String POLICY_UPDATE = "policy:update";
  public static final String POLICY_PUBLISH = "policy:publish";

  public static final String DEPARTMENT_QUERY = "department:query";
  public static final String DEPARTMENT_CREATE = "department:create";
  public static final String DEPARTMENT_UPDATE = "department:update";
  public static final String DEPARTMENT_DELETE = "department:delete";
  public static final String MEMBER_QUERY = "member:query";
  public static final String MEMBER_CREATE = "member:create";
  public static final String MEMBER_UPDATE = "member:update";
  public static final String MEMBER_DELETE = "member:delete";

  public static final String MEMBER_CHANGE_CREATE_SELF =
    "member-change:create:self";
  public static final String MEMBER_CHANGE_QUERY_SELF =
    "member-change:query:self";
  public static final String MEMBER_CHANGE_CANCEL_SELF =
    "member-change:cancel:self";
  public static final String MEMBER_CHANGE_QUERY_READER =
    "member-change:query:reader";
  public static final String MEMBER_CHANGE_QUERY_EMPLOYEE =
    "member-change:query:employee";
  public static final String READER_QUALIFICATION_APPROVE =
    "reader-qualification:approve";
  public static final String BORROW_QUOTA_CHANGE_APPROVE =
    "borrow-quota-change:approve";
  public static final String MEMBER_ACCOUNT_CHANGE_APPROVE =
    "member-account-change:approve";
  public static final String EMPLOYEE_PROFILE_CHANGE_APPROVE =
    "employee-profile-change:approve";
  public static final String EMPLOYEE_STATUS_CHANGE_APPROVE =
    "employee-status-change:approve";
  public static final String EMPLOYEE_ROLE_CHANGE_APPROVE =
    "employee-role-change:approve";
  public static final String PERMISSION_QUERY = "permission:query";
  public static final String PERMISSION_CREATE = "permission:create";
  public static final String PERMISSION_UPDATE = "permission:update";
  public static final String PERMISSION_DELETE = "permission:delete";

  public static final String BOOK_CATEGORY_QUERY = "book-category:query";
  public static final String BOOK_CATEGORY_CREATE = "book-category:create";
  public static final String BOOK_CATEGORY_UPDATE = "book-category:update";
  public static final String BOOK_CATEGORY_DELETE = "book-category:delete";

  public static final String BOOK_QUERY = "book:query";
  public static final String BOOK_CREATE = "book:create";
  public static final String BOOK_UPDATE = "book:update";
  public static final String BOOK_DELETE = "book:delete";

  public static final String BOOK_COPY_QUERY = "book-copy:query";
  public static final String BOOK_COPY_CREATE = "book-copy:create";
  public static final String BOOK_COPY_UPDATE = "book-copy:update";
  public static final String BOOK_COPY_DELETE = "book-copy:delete";

  public static final String BORROW_APPLICATION_CREATE =
    "borrow-application:create";
  public static final String BORROW_APPLICATION_QUERY =
    "borrow-application:query";
  public static final String BORROW_APPLICATION_CANCEL =
    "borrow-application:cancel";
  public static final String BORROW_APPLICATION_APPROVE =
    "borrow-application:approve";
  public static final String BORROW_APPLICATION_REJECT =
    "borrow-application:reject";
  public static final String BORROW_RECORD_QUERY = "borrow-record:query";
  public static final String BORROW_RECORD_RETURN = "borrow-record:return";

  public static final String READING_PLAN_QUERY_SELF =
    "reading-plan:query:self";
  public static final String READING_PLAN_CREATE_SELF =
    "reading-plan:create:self";
  public static final String READING_PLAN_UPDATE_SELF =
    "reading-plan:update:self";
  public static final String READING_PLAN_DELETE_SELF =
    "reading-plan:delete:self";
  public static final String READING_PLAN_QUERY = "reading-plan:query";
  public static final String READING_PLAN_CREATE = "reading-plan:create";
  public static final String READING_PLAN_UPDATE = "reading-plan:update";
  public static final String READING_PLAN_DELETE = "reading-plan:delete";

  private PermissionCodes() {}
  /** 全系统脱敏审计查询，需要显式ALL数据范围授权。 */
  public static final String OPERATION_LOG_QUERY = "operation-log:query";
}
