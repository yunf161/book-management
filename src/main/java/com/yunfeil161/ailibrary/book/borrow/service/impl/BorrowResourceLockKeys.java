package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationApproveRequest;
import com.yunfeil161.ailibrary.book.borrow.dto.BorrowApplicationCreateRequest;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType;
import com.yunfeil161.ailibrary.book.borrow.entity.BorrowApplication;
import com.yunfeil161.ailibrary.book.borrow.entity.BorrowRecord;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowApplicationMapper;
import com.yunfeil161.ailibrary.book.borrow.mapper.BorrowRecordMapper;
import com.yunfeil161.ailibrary.book.copy.entity.BookCopy;
import com.yunfeil161.ailibrary.book.copy.mapper.BookCopyMapper;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 在事务开始前解析借阅聚合涉及的稳定资源ID，使审批和两种归还入口使用
 * 完全相同的成员、图书、副本及业务单据锁键。
 */
@Component("borrowResourceLockKeys")
@RequiredArgsConstructor
public class BorrowResourceLockKeys {

  private final BorrowApplicationMapper applications;
  private final BorrowRecordMapper records;
  private final BookCopyMapper copies;

  /** 创建申请前按类型解析资源，避免同一借阅记录并发提交多条续借申请。 */
  public List<String> create(Long memberId, BorrowApplicationCreateRequest request) {
    List<String> keys = new ArrayList<>();
    keys.add("lock:resource:policy-current:ADMISSION");
    keys.add("lock:resource:policy-current:BORROWING");
    keys.add(member(memberId));
    keys.add("lock:resource:borrow-application:create:" + memberId);
    if (request == null) return keys;
    BorrowApplicationType type = request.getApplicationType() == null
      ? BorrowApplicationType.BORROW
      : request.getApplicationType();
    if (type == BorrowApplicationType.BORROW) {
      if (request.getBookId() != null) keys.add(book(request.getBookId()));
      return keys;
    }
    if (request.getBorrowRecordId() == null) return keys;
    BorrowRecord value = records.findById(request.getBorrowRecordId());
    keys.add(record(request.getBorrowRecordId()));
    if (value != null) appendReturnResources(keys, value);
    return keys;
  }

  public List<String> approval(Long applicationId, BorrowApplicationApproveRequest request) {
    List<String> keys = new ArrayList<>();
    keys.add("lock:resource:policy-current:ADMISSION");
    keys.add("lock:resource:policy-current:BORROWING");
    keys.add(application(applicationId));
    BorrowApplication value = applications.findById(applicationId);
    if (value == null) return keys;
    keys.add(member(value.getMemberId()));
    keys.add(book(value.getBookId()));
    if (value.getApplicationType() == BorrowApplicationType.RENEW) {
      if (value.getBorrowRecordId() != null) {
        keys.add(record(value.getBorrowRecordId()));
        BorrowRecord record = records.findById(value.getBorrowRecordId());
        if (record != null) appendReturnResources(keys, record);
      }
      return keys;
    }
    BookCopy copy = request != null && request.getCopyId() != null
      ? copies.findById(request.getCopyId())
      : copies.findAvailableByBookId(value.getBookId());
    if (copy != null) keys.add(copy(copy.getId()));
    return keys;
  }

  public List<String> returnByRecord(Long recordId) {
    List<String> keys = new ArrayList<>();
    keys.add(record(recordId));
    BorrowRecord value = records.findById(recordId);
    if (value == null) return keys;
    appendReturnResources(keys, value);
    return keys;
  }

  public List<String> returnByBarcode(String barcode) {
    List<String> keys = new ArrayList<>();
    if (barcode == null || barcode.isBlank()) return keys;
    BookCopy copy = copies.findByBarcode(barcode.trim());
    if (copy == null) return keys;
    keys.add(copy(copy.getId()));
    keys.add(book(copy.getBookId()));
    BorrowRecord value = records.findOpenByCopyId(copy.getId());
    if (value != null) {
      keys.add(record(value.getId()));
      keys.add(member(value.getMemberId()));
    }
    return keys;
  }

  /**
   * 将一次逾期同步即将修改的每条记录转换为记录级锁键。
   * 查询和定时更新因此使用相同的 borrow-record:{id} 键。
   */
  public List<String> overdue(LocalDateTime now) {
    if (now == null) return List.of();
    return records.findIdsToMarkOverdue(now).stream().map(this::record).toList();
  }

  private void appendReturnResources(List<String> keys, BorrowRecord value) {
    keys.add(member(value.getMemberId()));
    keys.add(copy(value.getCopyId()));
    BookCopy copy = copies.findById(value.getCopyId());
    if (copy != null) keys.add(book(copy.getBookId()));
  }

  private String member(Long id) { return "lock:resource:member:" + id; }
  private String book(Long id) { return "lock:resource:book:" + id; }
  private String copy(Long id) { return "lock:resource:book-copy:" + id; }
  private String application(Long id) { return "lock:resource:borrow-application:" + id; }
  private String record(Long id) { return "lock:resource:borrow-record:" + id; }
}
