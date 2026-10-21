package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowRecordMapper;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowOverdueService;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 逐条条件更新逾期状态，同事务生成通知；已逾期记录不会重复更新或重复通知。 */
@Service
@RequiredArgsConstructor
public class BorrowOverdueServiceImpl implements BorrowOverdueService {

  private final com.yunfeil161.ailibrary.audit.AuditRecorder audits;

  private final BorrowRecordMapper records;
  private final CacheClient cacheClient;
  private final com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper copies;
  private final com.yunfeil161.ailibrary.book.catalog.mapper.BookMapper books;
  private final com.yunfeil161.ailibrary.communication.notification.service.BusinessNotifications notifications;

  @Override
  @DistributedLock(keys = "@borrowResourceLockKeys.overdue(#now)")
  @Transactional
  public int synchronizeOverdueRecords(LocalDateTime now) {
    if (now == null) throw new IllegalArgumentException("now不能为空");
    var ids = records.findIdsToMarkOverdue(now);
    int affected = 0;
    for (Long id : ids) {
      var before = audits.before(
        com.yunfeil161.ailibrary.audit.AuditResource.BORROW_RECORD,
        id
      );
      // 条件更新只有首次越过应还时间的记录成功，通知与状态一起提交或回滚。
      if (records.markOverdue(id, now) != 1) continue;
      affected++;
      var record = records.findById(id);
      var copy = copies.findById(record.getCopyId());
      var book = copy == null ? null : books.findById(copy.getBookId());
      notifications.overdue(
        record,
        book == null ? "借阅图书" : book.getTitle()
      );
      cacheClient.deleteRequired(BookCacheKeys.BORROW_RECORD_BY_ID + id);
      audits.changed(
        com.yunfeil161.ailibrary.audit.AuditResource.BORROW_RECORD,
        id,
        "markOverdue",
        before
      );
    }
    return affected;
  }
}
