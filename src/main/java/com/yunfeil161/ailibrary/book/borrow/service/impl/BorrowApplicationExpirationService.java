package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowApplicationMapper;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists application expiration independently from the caller's transaction.
 * This keeps the EXPIRED status even when the caller subsequently reports a conflict.
 */
@Service
@RequiredArgsConstructor
public class BorrowApplicationExpirationService {

  private final BorrowApplicationMapper applicationMapper;
  private final CacheClient cacheClient;
  private final PendingBorrowApplicationGuard pendingGuard;
  private final com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications notifications;

  @DistributedLock(keys = "'lock:resource:borrow-application:' + #id")
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Audited(resource = AuditResource.BORROW_APPLICATION, id = "#id")
  public void expire(Long id, Long version, LocalDateTime now) {
    var application = applicationMapper.findById(id);
    if (
      application != null && applicationMapper.expire(id, version, now) == 1
    ) {
      // 独立过期事务与通知同时提交，重复扫描不会再次发送。
      notifications.expired(application);
      pendingGuard.clear(application);
      cacheClient.deleteRequired(BookCacheKeys.BORROW_APPLICATION_BY_ID + id);
    }
  }

  /** 查询不再修改状态；定时任务通过此入口分批取得到期申请。 */
  public java.util.List<com.yunfeil161.ailibrary.book.borrow.entity.BorrowApplication> findPendingExpired(
    LocalDateTime now,
    int limit
  ) {
    return applicationMapper.findPendingExpired(now, limit);
  }
}
