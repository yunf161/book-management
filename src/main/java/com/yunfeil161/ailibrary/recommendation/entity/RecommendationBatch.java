package com.yunfeil161.ailibrary.recommendation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 一次推荐快照；只开放 CURRENT 批次，每个成员最多保留三个批次。 */
@Data
public class RecommendationBatch {

  private Long id;
  private Long memberId;
  private String requestId;
  private String source;
  private String algorithmVersion;
  private String status;
  private LocalDateTime generatedAt;
  private LocalDateTime expiresAt;
}
