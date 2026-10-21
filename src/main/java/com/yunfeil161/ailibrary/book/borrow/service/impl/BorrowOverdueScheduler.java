package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.book.borrow.service.BorrowOverdueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 按配置周期将已过截止时间的借阅记录同步为逾期状态。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowOverdueScheduler {
    private final BorrowOverdueService overdueService;

    @Scheduled(cron = "${library.borrow.overdue-sync-cron:0 */5 * * * *}")
    public void synchronize() {
        int affected = overdueService.synchronizeOverdueRecords(LocalDateTime.now());
        if (affected > 0) log.info("同步逾期借阅记录：affected={}", affected);
    }
}
