package com.yunfeil161.ailibrary.book.reading.dto;

import com.yunfeil161.ailibrary.book.reading.enums.*;
import java.time.LocalDate;
import lombok.Data;

/** 新增阅读条目参数。 */
@Data
public class ReadingItemCreateRequest {

  private Long bookId;
  private Long expectedPlanVersion;
  private Integer sequenceNo;
  private Integer priority;
  private ReadingItemStatus status;
  private ReadingItemSource source;
  private LocalDate plannedStartDate;
  private LocalDate plannedFinishDate;
  private String note;
}
