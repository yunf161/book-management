package com.yunfeil161.ailibrary.book.category.entity;

import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 图书分类领域对象。 */
@Data
public class BookCategory {
    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private String description;
    private CatalogStatus status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
