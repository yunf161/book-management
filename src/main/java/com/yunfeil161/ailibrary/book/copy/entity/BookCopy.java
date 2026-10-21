package com.yunfeil161.ailibrary.book.copy.entity;

import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 可独立流转的图书实体副本。 */
@Data
public class BookCopy {
    private Long id;
    private Long bookId;
    /** 所属书目名称，仅用于查询展示，不写入 book_copy 表。 */
    private String bookTitle;
    private String barcode;
    private String callNumber;
    private String location;
    private BookCopyStatus status;
    private LocalDate acquiredAt;
    private BigDecimal price;
    private String conditionNote;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
