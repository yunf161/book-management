package com.yunfeil161.ailibrary.common.redis.lock;

/**
 * 项目业务写锁键。
 *
 * <p>第一版按业务聚合使用单锁，完整覆盖跨表检查，避免同时获取多把锁产生顺序
 * 不一致。确认真实热点后，可以在不改变业务代码的情况下进一步拆分锁粒度。</p>
 */
public final class BusinessLockKeys {

  public static final String IDENTITY_WRITE = "lock:business:identity:write";
  public static final String PERMISSION_WRITE = "lock:business:permission:write";

  /**
   * 图书域统一写锁。
   *
   * <p>分类、书目、副本、借阅和阅读计划会交叉检查或修改 book、book_copy，
   * 因此必须共用同一个锁键，才能保护跨模块的“检查后写入”流程。</p>
   */
  public static final String BOOK_WRITE = "lock:business:book:write";
  public static final String MEMBER = "lock:resource:member:";
  public static final String DEPARTMENT = "lock:resource:department:";
  public static final String ROLE = "lock:resource:role:";
  public static final String PERMISSION = "lock:resource:permission:";
  public static final String BOOK_CATEGORY = "lock:resource:book-category:";
  public static final String BOOK = "lock:resource:book:";
  public static final String BOOK_COPY = "lock:resource:book-copy:";
  public static final String BORROW_APPLICATION = "lock:resource:borrow-application:";
  public static final String BORROW_RECORD = "lock:resource:borrow-record:";
  public static final String READING_PLAN = "lock:resource:reading-plan:";
  public static final String READING_PLAN_ITEM = "lock:resource:reading-plan-item:";
  public static final String MEMBER_CHANGE_APPLICATION = "lock:resource:member-change-application:";

  private BusinessLockKeys() {}
}
