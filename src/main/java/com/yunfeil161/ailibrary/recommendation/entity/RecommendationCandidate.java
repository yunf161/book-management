package com.yunfeil161.ailibrary.recommendation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 候选召回所需的规则特征，不落库为推荐库存快照。 */
@Data
public class RecommendationCandidate {

  private Long id;
  // 仅向本地模型传递公开书目信息，不传递成员身份或借阅记录。
  private String title;
  private String author;
  private String description;
  private Long version;
  private Long categoryId;
  private Long availableCopies;
  private Long popularity;
  private LocalDateTime createdAt;
}
