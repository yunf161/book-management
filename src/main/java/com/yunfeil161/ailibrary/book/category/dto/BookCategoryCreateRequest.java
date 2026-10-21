package com.yunfeil161.ailibrary.book.category.dto;

import lombok.Data;

/** 新增图书分类参数。 */
@Data
public class BookCategoryCreateRequest {
    private String code;
    private String name;
    private Long parentId;
    private String description;
}
