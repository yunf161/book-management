package com.yunfeil161.ailibrary.book.reading.entity;

import com.yunfeil161.ailibrary.book.reading.enums.*;
import java.time.*;
import lombok.Data;

/** 阅读计划中的图书条目；planId 与 bookId 组成业务唯一键。 */
@Data
public class ReadingPlanItem {

  private Long id;
  private Long planId;
  private Long memberId;
  private Long bookId;
  private Long version;
  private String memberName;
  private String bookName;
  private String author;
  private String isbn;
  private String coverObjectKey;
  private Integer sequenceNo;
  private Integer priority;
  private Integer progressPercent;
  private ReadingItemStatus status;
  private ReadingItemSource source;
  private LocalDate plannedStartDate;
  private LocalDate plannedFinishDate;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String note;
}
