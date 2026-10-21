package com.yunfeil161.ailibrary.identity.application.service.impl;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 默认每分钟扫描一批；多实例重复扫描由资源锁、状态条件和版本共同防重。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MemberChangeExpirationScheduler {

  private static final int BATCH_SIZE = 100;
  private final MemberChangeExpirationService expiration;

  @Scheduled(
    cron = "${library.member.change-application-expire-cron:25 * * * * *}"
  )
  public void synchronize() {
    LocalDateTime now = LocalDateTime.now();
    var ids = expiration.findExpiredIds(now, BATCH_SIZE);
    int completed = 0;
    for (Long id : ids) {
      try {
        if (expiration.expire(id, now)) completed++;
      } catch (RuntimeException error) {
        // 单份异常不阻塞后续申请；该事务回滚，保留PENDING供下一轮重试。
        log.error("资料变更申请过期处理失败：applicationId={}", id, error);
      }
    }
    if (!ids.isEmpty()) log.info(
      "资料变更申请到期扫描：candidates={}, expired={}",
      ids.size(),
      completed
    );
  }
}
