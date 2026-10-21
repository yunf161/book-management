package com.yunfeil161.ailibrary.book.borrow.entity;

import com.yunfeil161.ailibrary.book.borrow.enums.BorrowRecordStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 借阅记录领域对象，对应 borrow_record 表。
 */
@Data
public class BorrowRecord {

  /** 主键ID。 */
  private Long id;
  /** 来源借阅申请ID。 */
  private Long applicationId;
  /** 借阅成员ID。 */
  private Long memberId;
  /** 实际借出的副本ID。 */
  private Long copyId;
  /** 实际借出时间。 */
  private LocalDateTime borrowedAt;
  /** 应归还时间。 */
  private LocalDateTime dueAt;
  /** 实际归还或确认遗失的结案时间。 */
  private LocalDateTime returnedAt;
  /** 当前业务状态。 */
  private BorrowRecordStatus status;
  /** 归还时确认的逾期天数。 */
  private Integer overdueDays;
  /** 批准借阅的馆员ID。 */
  private Long approvedBy;
  /** 办理归还的馆员ID。 */
  private Long returnedBy;
  /** 归还时的副本状况。 */
  private String returnConditionNote;
  /** 结案结果快照，避免之后维护副本时丢失当次归还书况。 */
  private BorrowReturnOutcome returnOutcome;
  /** 已审批通过的续借次数。 */
  private Integer renewCount;
  /** 最近一次续借审批通过时间。 */
  private LocalDateTime lastRenewedAt;
  /** 乐观锁版本。 */
  private Long version;
  /** 创建时间。 */
  private LocalDateTime createdAt;
  /** 最后更新时间。 */
  private LocalDateTime updatedAt;
}
