package com.yunfeil161.ailibrary.book.borrow.dto;

import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 借阅申请聚合查询参数；未设置的字段不参与过滤。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BorrowApplicationQueryRequest extends PageQuery {
    /** 主键ID。 */
    private Long id;
    /** 申请类型精确过滤。 */
    private BorrowApplicationType applicationType;
    /** 续借对应的借阅记录ID。 */
    private Long borrowRecordId;
    /** 状态过滤条件。 */
    private BorrowApplicationStatus status;
    /** 成员ID；读者端查询会强制覆盖为当前成员。 */
    private Long memberId;
    /** 成员姓名模糊查询条件。 */
    private String memberName;
    /** 目标书目ID。 */
    private Long bookId;
    /** 书名模糊查询或展示值。 */
    private String bookTitle;
    /** ISBN查询条件。 */
    private String isbn;
    /** 申请提交时间下限。 */
    private LocalDateTime submittedFrom;
    /** 申请提交时间上限。 */
    private LocalDateTime submittedTo;
    /** 审批人ID。 */
    private Long processedBy;
}
