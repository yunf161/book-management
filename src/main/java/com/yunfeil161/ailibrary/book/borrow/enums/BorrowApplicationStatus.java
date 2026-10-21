package com.yunfeil161.ailibrary.book.borrow.enums;

/**
 * 借阅申请状态。
 */
public enum BorrowApplicationStatus {
    /** 等待馆员处理。 */ PENDING,
    /** 已批准并生成借阅记录。 */ APPROVED,
    /** 馆员已拒绝。 */ REJECTED,
    /** 读者主动取消。 */ CANCELLED,
    /** 超过申请有效期。 */ EXPIRED
}
