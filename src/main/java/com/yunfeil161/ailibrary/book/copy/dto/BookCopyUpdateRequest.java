package com.yunfeil161.ailibrary.book.copy.dto;

import com.yunfeil161.ailibrary.book.copy.enums.BookCopyStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 选择性修改图书副本参数。 */
@Data
public class BookCopyUpdateRequest {
    private String barcode;
    private String callNumber;
    private String location;
    private BookCopyStatus status;
    private LocalDate acquiredAt;
    private BigDecimal price;
    private String conditionNote;
    private Long expectedVersion;

    private boolean callNumberProvided;
    private boolean acquiredAtProvided;
    private boolean priceProvided;
    private boolean conditionNoteProvided;

    public void setCallNumber(String callNumber) { this.callNumber = callNumber; this.callNumberProvided = true; }
    public void setAcquiredAt(LocalDate acquiredAt) { this.acquiredAt = acquiredAt; this.acquiredAtProvided = true; }
    public void setPrice(BigDecimal price) { this.price = price; this.priceProvided = true; }
    public void setConditionNote(String conditionNote) { this.conditionNote = conditionNote; this.conditionNoteProvided = true; }
}
