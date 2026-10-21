package com.yunfeil161.ailibrary.recommendation.dto;

import com.yunfeil161.ailibrary.recommendation.entity.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Redis 仅保存推荐标识和解释，避免缓存库存、封面等可变化字段。 */
@Data
public class RecommendationSnapshot {

  private RecommendationBatch batch;
  private List<RecommendationItem> items = new ArrayList<>();
}
