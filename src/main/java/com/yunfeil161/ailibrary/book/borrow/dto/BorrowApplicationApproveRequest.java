package com.yunfeil161.ailibrary.book.borrow.dto;

import lombok.Data;

/**
 * 馆员批准借阅申请时提交的参数。
 */
@Data
public class BorrowApplicationApproveRequest {
    /** 指定副本ID；为空时由系统选择可借副本。 */
    private Long copyId;
    /** 批准的借阅天数。 */
    private Integer approvedDays;
    /** 审批意见或拒绝原因。 */
    private String decisionRemark;
    /** 客户端读取到的版本号，用于乐观锁校验。 */
    private Long expectedVersion;
}
