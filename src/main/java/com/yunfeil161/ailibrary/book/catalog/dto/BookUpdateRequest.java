package com.yunfeil161.ailibrary.book.catalog.dto;

import com.yunfeil161.ailibrary.book.common.enums.CatalogStatus;
import java.time.LocalDate;
import lombok.Data;

/** 选择性修改图书书目参数。 */
@Data
public class BookUpdateRequest {

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
  private CatalogStatus status;
  private Long expectedVersion;

  private boolean subtitleProvided;
  private boolean isbnProvided;
  private boolean publisherProvided;
  private boolean publishedDateProvided;
  private boolean editionProvided;
  private boolean descriptionProvided;
  private boolean coverObjectKeyProvided;

  public void setSubtitle(String subtitle) {
    this.subtitle = subtitle;
    this.subtitleProvided = true;
  }

  public void setIsbn(String isbn) {
    this.isbn = isbn;
    this.isbnProvided = true;
  }

  public void setPublisher(String publisher) {
    this.publisher = publisher;
    this.publisherProvided = true;
  }

  public void setPublishedDate(LocalDate publishedDate) {
    this.publishedDate = publishedDate;
    this.publishedDateProvided = true;
  }

  public void setEdition(String edition) {
    this.edition = edition;
    this.editionProvided = true;
  }

  public void setDescription(String description) {
    this.description = description;
    this.descriptionProvided = true;
  }

  public void setCoverObjectKey(String coverObjectKey) {
    this.coverObjectKey = coverObjectKey;
    this.coverObjectKeyProvided = true;
  }
}
