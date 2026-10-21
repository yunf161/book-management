package com.yunfeil161.ailibrary.book.copy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 给路径中指定图书新增副本的参数。 */
@Data
public class BookCopyCreateRequest {
    private String barcode;
    private String callNumber;
    private String location;
    private LocalDate acquiredAt;
    private BigDecimal price;
    private String conditionNote;
}
