package com.yunfeil161.ailibrary.book.borrow.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时将待审批且已经到期的申请改为 EXPIRED，并逐条清除 ID 缓存。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowApplicationExpirationScheduler {

  private static final int BATCH_SIZE = 100;
  private final BorrowApplicationExpirationService expirationService;

  @Scheduled(cron = "${library.borrow.application-expire-cron:15 * * * * *}")
  public void synchronize() {
    LocalDateTime now = LocalDateTime.now();
    var expired = expirationService.findPendingExpired(now, BATCH_SIZE);
    for (var application : expired) {
      expirationService.expire(application.getId(), application.getVersion(), now);
    }
    if (!expired.isEmpty()) {
      log.info("同步过期借阅申请：candidates={}", expired.size());
    }
  }
}
