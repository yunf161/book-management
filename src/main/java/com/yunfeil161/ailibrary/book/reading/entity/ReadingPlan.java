package com.yunfeil161.ailibrary.book.reading.entity;

import com.yunfeil161.ailibrary.book.reading.enums.ReadingPlanStatus;
import java.time.*;
import lombok.Data;

/** 一名成员唯一的阅读计划。 */
@Data
public class ReadingPlan {

  private Long id;
  private Long memberId;
  private Long version;
  private String memberName;
  private String name;
  private String description;
  /** 目标完成书目数，不限制书单容量；完成计划时必须达到本目标。 */
  private Integer targetBooks;
  private LocalDate startDate;
  private LocalDate endDate;
  private ReadingPlanStatus status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
