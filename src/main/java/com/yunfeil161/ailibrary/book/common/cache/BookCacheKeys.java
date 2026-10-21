package com.yunfeil161.ailibrary.book.common.cache;

/** 图书模块缓存键及统一过期时间。 */
public final class BookCacheKeys {

  /** 分类、书目和副本按 ID 查询时使用的缓存前缀。 */
  public static final String CATEGORY_BY_ID = "book:category:id:";
  public static final String BOOK_BY_ID = "book:catalog:id:";
  public static final String COPY_BY_ID = "book:copy:id:";

  /** 借阅申请和借阅记录按 ID 查询时使用的缓存前缀。 */
  public static final String BORROW_APPLICATION_BY_ID =
    "book:borrow:application:id:";
  public static final String BORROW_RECORD_BY_ID = "book:borrow:record:id:";

  /** 阅读计划按成员缓存，阅读条目按成员和图书的组合键缓存。 */
  public static final String READING_PLAN_BY_MEMBER =
    "book:reading:plan:member:";
  public static final String READING_ITEM_BY_MEMBER_BOOK =
    "book:reading:item:member-book:";

  /** 不同业务数据独立设置有效期，便于按变化频率调整缓存策略。 */
  public static final long ID_TTL_MINUTES = 30L;
  public static final long BORROW_TTL_MINUTES = 10L;
  public static final long READING_PLAN_TTL_MINUTES = 20L;
  public static final long READING_ITEM_TTL_MINUTES = 10L;

  /**
   * 生成唯一阅读条目缓存键。
   *
   * @param memberId 条目所属成员 ID
   * @param bookId   条目关联图书 ID
   * @return 包含成员与图书维度的完整缓存键
   */
  public static String readingItemKey(Long memberId, Long bookId) {
    return READING_ITEM_BY_MEMBER_BOOK + memberId + ":" + bookId;
  }

  private BookCacheKeys() {}
}
