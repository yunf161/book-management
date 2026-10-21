package com.yunfeil161.ailibrary.book.catalog.dto;

import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 图书书目全字段组合查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookQueryRequest extends PageQuery {
    private Long id;
    private Long categoryId;
    private String keyword;
    private String title;
    private String subtitle;
    private String author;
    private String isbn;
    private String publisher;
    private LocalDate publishedDate;
    private String edition;
    private String language;
    private String description;
    private String coverObjectKey;
    private CatalogStatus status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean availableOnly;
}
