package com.yunfeil161.ailibrary.book.borrow.entity;

import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationStatus;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 借阅申请领域对象，对应 borrow_application 表。
 */
@Data
public class BorrowApplication {
    /** 提交时生效政策快照；旧申请没有快照时不允许审批通过。 */
    private Long borrowingPolicyId;
    private Long borrowingPolicyVersion;

    /** 主键ID。 */
    private Long id;
    /** 申请类型：BORROW 表示借阅，RENEW 表示续借。 */
    private BorrowApplicationType applicationType;
    /** 借阅成员ID。 */
    private Long memberId;
    /** 申请的书目ID。 */
    private Long bookId;
    /** 续借所对应的借阅记录；普通借阅申请为空。 */
    private Long borrowRecordId;
    /** 申请借阅天数。 */
    private Integer requestedDays;
    /** 申请理由。 */
    private String reason;
    /** 当前业务状态。 */
    private BorrowApplicationStatus status;
    /** 申请提交时间。 */
    private LocalDateTime submittedAt;
    /** 待审批申请的过期时间。 */
    private LocalDateTime expiresAt;
    /** 审批处理时间。 */
    private LocalDateTime processedAt;
    /** 审批成员ID。 */
    private Long processedBy;
    /** 审批通过时确定的副本ID。 */
    private Long approvedCopyId;
    /** 审批批准的借阅天数。 */
    private Integer approvedDays;
    /** 审批意见。 */
    private String decisionRemark;
    /** 乐观锁版本。 */
    private Long version;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
