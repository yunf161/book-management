package com.yunfeil161.ailibrary.book.borrow.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅申请查询结果，在申请数据上补充成员、书目和副本展示信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BorrowApplicationDetailVO extends BorrowApplication {
    /** 成员姓名。 */
    private String memberName;
    /** 书名。 */
    private String bookTitle;
    /** 作者。 */
    private String bookAuthor;
    /** ISBN。 */
    private String isbn;
    /** 封面对象存储路径。 */
    private String coverObjectKey;
    /** 批准副本的馆藏条码。 */
    private String approvedCopyBarcode;
}
