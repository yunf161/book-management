package com.yunfeil161.ailibrary.book.category.dto;

import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import lombok.Data;

/** 选择性修改图书分类参数，code 创建后不可修改。 */
@Data
public class BookCategoryUpdateRequest {
    private String name;
    private Long parentId;
    private boolean parentIdProvided;
    private String description;
    private boolean descriptionProvided;
    private CatalogStatus status;
    private Long expectedVersion;

    public void setParentId(Long parentId) {
        this.parentId = parentId;
        this.parentIdProvided = true;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }
}
