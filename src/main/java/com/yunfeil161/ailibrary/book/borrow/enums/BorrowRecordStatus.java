package com.yunfeil161.ailibrary.book.borrow.enums;

/**
 * 借阅记录状态。
 */
public enum BorrowRecordStatus {
    /** 正常借阅中。 */ BORROWING,
    /** 已超过应还时间且尚未归还。 */ OVERDUE,
    /** 已完成归还。 */ RETURNED,
    /** 借阅期间确认遗失。 */ LOST
}
