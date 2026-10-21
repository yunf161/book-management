package com.yunfeil161.ailibrary.book.borrow.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

/** 统一计算逾期天数：不足一天按一天，超过整天的部分继续向上取整。 */
final class BorrowOverdueCalculator {
    private static final long SECONDS_PER_DAY = 24 * 60 * 60;

    private BorrowOverdueCalculator() {
    }

    static int calculate(LocalDateTime dueAt, LocalDateTime now) {
        if (dueAt == null || now == null || !now.isAfter(dueAt)) return 0;
        long seconds = Duration.between(dueAt, now).getSeconds();
        long days = (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
        return Math.toIntExact(Math.max(1, days));
    }
}
