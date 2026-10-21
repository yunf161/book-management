package com.yunfeil161.ailibrary.book.borrow.dto;

import lombok.Data;

/**
 * 读者取消借阅申请时提交的参数。
 */
@Data
public class BorrowApplicationCancelRequest {
    /** 客户端读取到的版本号，用于乐观锁校验。 */
    private Long expectedVersion;
}
