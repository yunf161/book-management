package com.yunfeil161.ailibrary.book.catalog.dto;

import java.time.LocalDate;
import lombok.Data;

/** 新增图书书目参数。 */
@Data
public class BookCreateRequest {

  /** 可选标签ID列表；更新不传则保留，传空列表则清除。 */
  private java.util.List<Long> tagIds;
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
}
