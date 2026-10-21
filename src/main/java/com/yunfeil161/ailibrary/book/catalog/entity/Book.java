package com.yunfeil161.ailibrary.book.catalog.entity;

import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 一种具体出版版本的图书书目。 */
@Data
public class Book {
    private Long id;
    private Long categoryId;
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
}
