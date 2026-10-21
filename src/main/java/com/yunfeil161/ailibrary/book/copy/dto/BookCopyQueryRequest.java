package com.yunfeil161.ailibrary.book.copy.dto;

import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 图书副本全字段组合查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookCopyQueryRequest extends PageQuery {
    private Long id;
    private Long bookId;
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
