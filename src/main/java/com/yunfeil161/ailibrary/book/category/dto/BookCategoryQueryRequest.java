package com.yunfeil161.ailibrary.book.category.dto;

import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 图书分类全字段组合查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BookCategoryQueryRequest extends PageQuery {
    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private String description;
    private CatalogStatus status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean rootOnly;
}
