package com.yunfeil161.ailibrary.recommendation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 保留图书 ID 快照，使反馈不依赖已被覆盖的历史条目。 */
@Data
public class RecommendationFeedback {

  private Long id;
  private Long memberId;
  private Long batchId;
  private Long itemId;
  private Long bookId;
  private String kind;
  private Boolean historical;
  private LocalDateTime createdAt;
}
