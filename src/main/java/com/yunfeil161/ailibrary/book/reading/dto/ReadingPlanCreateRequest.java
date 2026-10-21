package com.yunfeil161.ailibrary.book.reading.dto;

import com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus;
import java.time.LocalDate;
import lombok.Data;

/** 新增阅读计划参数；普通接口忽略 memberId。 */
@Data
public class ReadingPlanCreateRequest {

  private Long memberId;
  private String name;
  private String description;
  private Integer targetBooks;
  private LocalDate startDate;
  private LocalDate endDate;
  private ReadingPlanStatus status;
}
