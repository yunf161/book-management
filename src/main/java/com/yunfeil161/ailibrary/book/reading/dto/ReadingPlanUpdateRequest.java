package com.yunfeil161.ailibrary.book.reading.dto;

import com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus;
import java.time.LocalDate;
import lombok.Data;

/** 修改阅读计划参数。 */
@Data
public class ReadingPlanUpdateRequest {

  private String name;
  private String description;
  private Integer targetBooks;
  private LocalDate startDate;
  private LocalDate endDate;
  private ReadingPlanStatus status;
  private Long version;
}
