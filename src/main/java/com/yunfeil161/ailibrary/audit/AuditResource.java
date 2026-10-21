package com.yunfeil161.ailibrary.audit;

/** 审计可读取的表必须在这里静态列举，客户端不能指定任意表或列。 */
public enum AuditResource {
  AI_HANDOFF("ai_handoff"),
  MEMBER("member"),
  DEPARTMENT("department"),
  PERMISSION("permission"),
  BOOK_CATEGORY("book_category"),
  BOOK("book"),
  BOOK_COPY("book_copy"),
  BORROW_APPLICATION("borrow_application"),
  BORROW_RECORD("borrow_record"),
  MEMBER_CHANGE("member_change_application"),
  POLICY("policy_version"),
  READING_PLAN_MEMBER("reading_plan");

  final String table;

  AuditResource(String table) {
    this.table = table;
  }
}
