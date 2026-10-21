package com.yunfeil161.ailibrary.book.reading.dto;

import com.yunfeil161.ailibrary.book.reading.enums.ReadingItemStatus;
import java.time.LocalDate;
import lombok.Data;

/** 修改阅读条目参数，包含主表和条目两级版本。 */
@Data
public class ReadingItemUpdateRequest {

  private Integer sequenceNo;
  private Integer priority;
  private Integer progressPercent;
  private ReadingItemStatus status;
  private LocalDate plannedStartDate;
  private LocalDate plannedFinishDate;
  private String note;
  private Long version;
  private Long expectedPlanVersion;
}
