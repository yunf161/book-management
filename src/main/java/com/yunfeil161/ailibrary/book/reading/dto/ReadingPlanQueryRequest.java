package com.yunfeil161.ailibrary.book.reading.dto;

import com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus;
import java.time.LocalDate;
import lombok.Data;

/** 管理端聚合查询阅读计划参数。 */
@Data
public class ReadingPlanQueryRequest {

  private Integer pageNum;
  private Integer pageSize;
  private Long memberId;
  private String memberKeyword;
  private String planName;
  private ReadingPlanStatus status;
  private LocalDate startDateFrom;
  private LocalDate startDateTo;
}
