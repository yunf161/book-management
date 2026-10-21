package com.yunfeil161.ailibrary.book.borrow.mapper;

import com.yunfeil161.ailibrary.book.borrow.dto.BorrowRecordQueryRequest;
import com.yunfeil161.ailibrary.book.borrow.entity.*;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowRecordStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowReturnOutcome;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/**
 * 借阅记录数据访问接口。
 */
@Mapper
public interface BorrowRecordMapper {
  /** 按组合条件查询。 */
  List<BorrowRecordDetailVO> find(
    @Param("q") BorrowRecordQueryRequest q,
    @Param("now") LocalDateTime now
  );

  /** 按主键查询。 */
  BorrowRecord findById(@Param("id") Long id);

  /** 查询副本当前未完成的借阅记录。 */
  BorrowRecord findOpenByCopyId(@Param("copyId") Long copyId);

  /** 统计指定副本未完成的借阅记录，用于发现异常重复数据。 */
  int countOpenByCopyId(@Param("copyId") Long copyId);

  /** 统计成员对指定书目的未完成借阅。 */
  int countOpenByMemberBook(
    @Param("memberId") Long memberId,
    @Param("bookId") Long bookId
  );

  /** 统计成员当前逾期记录。 */
  int countOverdue(
    @Param("memberId") Long memberId,
    @Param("now") LocalDateTime now
  );

  /** 插入新记录。 */
  int insert(BorrowRecord record);

  /** 按版本延长归还期限并累计续借次数。 */
  int renew(
    @Param("id") Long id,
    @Param("version") Long version,
    @Param("dueAt") LocalDateTime dueAt,
    @Param("now") LocalDateTime now
  );

  /** 按状态和版本完成归还。 */
  int finishReturn(
    @Param("id") Long id,
    @Param("version") Long version,
    @Param("returnedBy") Long returnedBy,
    @Param("note") String note,
    @Param("now") LocalDateTime now,
    @Param("overdueDays") Integer overdueDays,
    @Param("status") BorrowRecordStatus status,
    @Param("returnOutcome") BorrowReturnOutcome returnOutcome
  );

  /** 将到期且未归还的正常借阅批量同步为逾期状态。 */
  /** 状态条件保证只有首次进入逾期的记录触发通知。 */
  int markOverdue(@Param("id") Long id, @Param("now") LocalDateTime now);

  int synchronizeOverdue(@Param("now") LocalDateTime now);

  /** 查询本次将由 BORROWING 转为 OVERDUE 的记录，用于清理按ID缓存。 */
  List<Long> findIdsToMarkOverdue(@Param("now") LocalDateTime now);
}
