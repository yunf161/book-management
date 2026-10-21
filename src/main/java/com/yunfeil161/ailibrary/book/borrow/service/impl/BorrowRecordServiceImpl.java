package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowRecordQueryRequest;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowReturnRequest;
import com.yunfeil161.ailibrary.book.borrow.entity.BorrowRecord;
import com.yunfeil161.ailibrary.book.borrow.entity.BorrowRecordDetailVO;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowRecordStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowMemberMapper;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowRecordMapper;
import com.yunfeil161.ailibrary.book.borrow.service.BorrowRecordService;
import com.yunfeil161.ailibrary.book.common.cache.BookCacheKeys;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.identity.entity.Member;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 借阅记录查询和归还业务。 */
@Service
@RequiredArgsConstructor
public class BorrowRecordServiceImpl implements BorrowRecordService {

  private final BorrowAccess access;
  private final BorrowRecordMapper records;
  private final BookCopyMapper copies;
  private final BorrowMemberMapper members;
  private final CacheClient cacheClient;

  @Override
  @DistributedLock(
    keys = "'lock:resource:borrow-record:' + (#query != null && #query.id != null ? #query.id : 'mine-query')",
    mode = LockMode.READ
  )
  public PageResult<BorrowRecordDetailVO> queryMine(
    BorrowRecordQueryRequest query
  ) {
    LoginSession session = access.require(
      PermissionCodes.BORROW_RECORD_QUERY,
      DataScope.SELF
    );
    if (query == null) query = new BorrowRecordQueryRequest();
    query.setMemberId(session.getMemberId());
    return query(query);
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:borrow-record:' + (#query != null && #query.id != null ? #query.id : 'admin-query')",
    mode = LockMode.READ
  )
  public PageResult<BorrowRecordDetailVO> queryAdmin(
    BorrowRecordQueryRequest query
  ) {
    access.require(PermissionCodes.BORROW_RECORD_QUERY, DataScope.ALL);
    if (query == null) query = new BorrowRecordQueryRequest();
    return query(query);
  }

  private PageResult<BorrowRecordDetailVO> query(
    BorrowRecordQueryRequest query
  ) {
    if (isIdOnlyQuery(query)) {
      LocalDateTime now = LocalDateTime.now();
      BorrowRecordDetailVO row = cacheClient.queryWithPassThrough(
        BookCacheKeys.BORROW_RECORD_BY_ID,
        query.getId(),
        BorrowRecordDetailVO.class,
        ignored -> findOne(query, now),
        BookCacheKeys.BORROW_TTL_MINUTES,
        TimeUnit.MINUTES
      );
      if (
        row == null ||
        (query.getMemberId() != null &&
          !query.getMemberId().equals(row.getMemberId()))
      ) {
        throw new NotFoundException("借阅记录不存在");
      }
      return new PageResult<>(1L, List.of(row));
    }
    PageHelper.startPage(
      query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage(),
      query.getPageSize() == null || query.getPageSize() < 1
        ? 10
        : Math.min(query.getPageSize(), 100)
    );
    List<BorrowRecordDetailVO> rows = records.find(query, LocalDateTime.now());
    if (query.getId() != null && rows.isEmpty()) throw new NotFoundException(
      "借阅记录不存在"
    );
    PageInfo<BorrowRecordDetailVO> page = new PageInfo<>(rows);
    return new PageResult<>(page.getTotal(), page.getList());
  }

  @Override
  @DistributedLock(keys = "@borrowResourceLockKeys.returnByRecord(#id)")
  @Transactional
  @Audited(resource = AuditResource.BORROW_RECORD, id = "#id")
  public BorrowRecord returnByRecord(Long id, BorrowReturnRequest request) {
    access.require(PermissionCodes.BORROW_RECORD_RETURN, DataScope.ALL);
    if (id == null || id <= 0) throw new WrongFormatException(
      "借阅记录ID格式错误"
    );
    if (
      request == null ||
      request.getExpectedVersion() == null ||
      request.getExpectedVersion() < 0
    ) throw new WrongFormatException("expectedVersion不能为空且不能小于0");
    normalizeReturnNote(request);
    return doReturn(id, request);
  }

  @Override
  @DistributedLock(keys = "@borrowResourceLockKeys.returnByBarcode(#barcode)")
  @Transactional
  @Audited(
    resource = AuditResource.BORROW_RECORD,
    id = "#snapshots.recordByBarcode(#barcode)"
  )
  public BorrowRecord returnByBarcode(
    String barcode,
    BorrowReturnRequest request
  ) {
    access.require(PermissionCodes.BORROW_RECORD_RETURN, DataScope.ALL);
    if (barcode == null || barcode.isBlank()) throw new WrongFormatException(
      "条码不能为空"
    );
    BookCopy copy = copies.findByBarcode(barcode.trim());
    if (copy == null) throw new NotFoundException("副本不存在");
    int openCount = records.countOpenByCopyId(copy.getId());
    if (openCount > 1) throw new IllegalStateException(
      "同一副本存在多条未完成借阅记录"
    );
    if (openCount == 0) throw new NotFoundException("没有未完成借阅记录");
    BorrowRecord record = records.findOpenByCopyId(copy.getId());
    if (request == null) request = new BorrowReturnRequest();
    normalizeReturnNote(request);
    request.setExpectedVersion(record.getVersion());
    return doReturn(record.getId(), request);
  }

  private void normalizeReturnNote(BorrowReturnRequest request) {
    // 原客户端不传结果仍按正常归还；不可由客户端直接指定副本任意状态。
    if (request.getReturnOutcome() == null) {
      request.setReturnOutcome(BorrowReturnOutcome.NORMAL);
    }
    String note = request.getReturnConditionNote();
    if (note == null) return;
    note = note.trim();
    if (note.length() > 500) throw new WrongFormatException(
      "归还状况说明不能超过500个字符"
    );
    request.setReturnConditionNote(note.isEmpty() ? null : note);
  }

  @Transactional
  protected BorrowRecord doReturn(Long id, BorrowReturnRequest request) {
    LoginSession session = access.require(
      PermissionCodes.BORROW_RECORD_RETURN,
      DataScope.ALL
    );
    BorrowRecord record = records.findById(id);
    if (record == null) throw new NotFoundException("借阅记录不存在");
    if (
      (record.getStatus() != BorrowRecordStatus.BORROWING &&
        record.getStatus() != BorrowRecordStatus.OVERDUE) ||
      record.getReturnedAt() != null
    ) throw new ConflictException("借阅记录已经归还或当前状态不允许归还");
    BookCopy copy = copies.findById(record.getCopyId());
    if (copy == null) throw new NotFoundException("副本不存在");
    if (
      copy.getStatus() != BookCopyStatus.BORROWED
    ) throw new ConflictException("副本当前不是已借出状态");
    Member member = members.findById(record.getMemberId());
    if (member == null) throw new NotFoundException("成员不存在");
    if (
      member.getBorrowedBookCount() == null ||
      member.getBorrowedBookCount() <= 0
    ) throw new ConflictException("成员已借数量异常");

    LocalDateTime now = LocalDateTime.now();
    int overdueDays = BorrowOverdueCalculator.calculate(record.getDueAt(), now);
    BorrowReturnOutcome outcome = request.getReturnOutcome();
    BorrowRecordStatus recordStatus =
      outcome == BorrowReturnOutcome.LOST
        ? BorrowRecordStatus.LOST
        : BorrowRecordStatus.RETURNED;
    BookCopyStatus copyStatus = switch (outcome) {
      case NORMAL -> BookCopyStatus.AVAILABLE;
      case DAMAGED -> BookCopyStatus.MAINTENANCE;
      case LOST -> BookCopyStatus.LOST;
    };
    // 损坏/遗失同样结束本次借阅，逾期计算截至结案时刻。
    // 三表更新和缓存失效仍处于原事务及同一组资源写锁内，数量只扣减一次。
    if (
      records.finishReturn(
        id,
        request.getExpectedVersion(),
        session.getMemberId(),
        request.getReturnConditionNote(),
        now,
        overdueDays,
        recordStatus,
        outcome
      ) != 1
    ) throw new ConflictException("借阅记录状态或版本已变化");
    if (
      copies.returnCopy(
        copy.getId(),
        copy.getVersion(),
        request.getReturnConditionNote(),
        copyStatus
      ) != 1
    ) throw new ConflictException("副本状态已变化");
    if (
      members.decrementBorrowed(member.getId(), member.getVersion()) != 1
    ) throw new ConflictException("成员借阅数量已变化");
    cacheClient.deleteRequired(BookCacheKeys.COPY_BY_ID + copy.getId());
    cacheClient.deleteRequired(BookCacheKeys.BOOK_BY_ID + copy.getBookId());
    cacheClient.deleteRequired(BookCacheKeys.BORROW_RECORD_BY_ID + id);
    // 归还减少已借数量，不能让个人中心继续显示借阅前的缓存。
    cacheClient.deleteRequired("member:reader:" + member.getId());
    cacheClient.deleteRequired("member:employee:" + member.getId());
    return records.findById(id);
  }

  private BorrowRecordDetailVO findOne(
    BorrowRecordQueryRequest query,
    LocalDateTime now
  ) {
    List<BorrowRecordDetailVO> rows = records.find(query, now);
    return rows.isEmpty() ? null : rows.get(0);
  }

  private boolean isIdOnlyQuery(BorrowRecordQueryRequest q) {
    return (
      q.getId() != null &&
      q.getMemberName() == null &&
      q.getBookId() == null &&
      q.getBookTitle() == null &&
      q.getIsbn() == null &&
      q.getCopyBarcode() == null &&
      q.getStatus() == null &&
      q.getReturnOutcome() == null &&
      q.getCurrentOnly() == null &&
      q.getOverdueOnly() == null &&
      q.getBorrowedFrom() == null &&
      q.getBorrowedTo() == null &&
      q.getDueFrom() == null &&
      q.getDueTo() == null
    );
  }
}
