package com.yunfeil161.ailibrary.book.borrow.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 借阅记录查询结果，在记录数据上补充成员、书目和副本展示信息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BorrowRecordDetailVO extends BorrowRecord {
    /** 成员姓名。 */
    private String memberName;
    /** 申请的书目ID。 */
    private Long bookId;
    /** 书名。 */
    private String title;
    /** 作者。 */
    private String author;
    /** ISBN。 */
    private String isbn;
    /** 封面对象存储路径。 */
    private String coverObjectKey;
    /** 馆藏条码。 */
    private String barcode;
    /** 馆藏位置。 */
    private String location;
    /** 查询时实时计算的逾期天数。 */
    private Integer currentOverdueDays;
}
