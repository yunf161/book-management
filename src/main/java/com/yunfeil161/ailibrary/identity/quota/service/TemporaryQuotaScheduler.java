package com.yunfeil161.ailibrary.identity.quota.service;

import com.yunfeil161.ailibrary.identity.quota.mapper.TemporaryQuotaMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 分批回收已过期字段，多实例通过同一成员资源锁及条件更新安全执行。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TemporaryQuotaScheduler {

  private final TemporaryQuotaMapper quotas;
  private final TemporaryQuotaService service;

  @Scheduled(
    cron = "${library.member.temporary-quota-cleanup-cron:0 * * * * *}"
  )
  public void synchronize() {
    LocalDateTime now = LocalDateTime.now();
    for (Long id : quotas.findExpired(now)) {
      try {
        if (service.clearExpired(id, now)) log.info(
          "临时借阅额度到期清理：memberId={}",
          id
        );
      } catch (RuntimeException exception) {
        // 单个成员失败不阻止其他成员回收；保留数据库字段供下一轮重试。
        log.error("临时借阅额度清理失败：memberId={}", id, exception);
      }
    }
  }
}
