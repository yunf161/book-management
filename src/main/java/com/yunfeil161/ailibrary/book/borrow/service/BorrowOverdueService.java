package com.yunfeil161.ailibrary.book.borrow.service;

import java.time.LocalDateTime;

/** 维护借阅记录持久化逾期状态。 */
public interface BorrowOverdueService {
    int synchronizeOverdueRecords(LocalDateTime now);
}
