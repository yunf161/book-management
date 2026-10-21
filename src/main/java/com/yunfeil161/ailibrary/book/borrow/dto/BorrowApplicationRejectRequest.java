package com.yunfeil161.ailibrary.book.borrow.dto;

import lombok.Data;

/**
 * 馆员拒绝借阅申请时提交的参数。
 */
@Data
public class BorrowApplicationRejectRequest {
    /** 审批意见或拒绝原因。 */
    private String decisionRemark;
    /** 客户端读取到的版本号，用于乐观锁校验。 */
    private Long expectedVersion;
}
