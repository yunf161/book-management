package com.yunfeil161.ailibrary.book.borrow.dto;

import com.yunfeil161.ailibrary.book.borrow.enums.BorrowRecordStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅记录聚合查询参数；支持当前借阅和逾期记录筛选。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BorrowRecordQueryRequest extends PageQuery {

  /** 主键ID。 */
  private Long id;
  /** 成员ID；读者端查询会强制覆盖为当前成员。 */
  private Long memberId;
  /** 成员姓名模糊查询条件。 */
  private String memberName;
  /** 书目ID。 */
  private Long bookId;
  /** 书名模糊查询或展示值。 */
  private String bookTitle;
  /** ISBN查询条件。 */
  private String isbn;
  /** 副本条码模糊查询条件。 */
  private String copyBarcode;
  /** 状态过滤条件。 */
  private BorrowRecordStatus status;
  /** 按正常归还、损坏归还或遗失结案筛选，保留聚合查询。 */
  private BorrowReturnOutcome returnOutcome;
  /** 是否只查询尚未归还的记录。 */
  private Boolean currentOnly;
  /** 是否只查询当前逾期记录。 */
  private Boolean overdueOnly;
  /** 借出时间下限。 */
  private LocalDateTime borrowedFrom;
  /** 借出时间上限。 */
  private LocalDateTime borrowedTo;
  /** 应还时间下限。 */
  private LocalDateTime dueFrom;
  /** 应还时间上限。 */
  private LocalDateTime dueTo;
}
