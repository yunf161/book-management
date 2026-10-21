package com.yunfeil161.ailibrary.book.borrow.dto;

import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType;
import lombok.Data;

/**
 * 读者创建借阅申请时提交的参数。
 */
@Data
public class BorrowApplicationCreateRequest {
    /** 申请类型；为空时兼容旧客户端并按 BORROW 处理。 */
    private BorrowApplicationType applicationType;
    /** 目标书目ID。 */
    private Long bookId;
    /** 续借对应的借阅记录ID，仅 RENEW 使用。 */
    private Long borrowRecordId;
    /** 申请借阅天数。 */
    private Integer requestedDays;
    /** 借阅理由。 */
    private String reason;
}
