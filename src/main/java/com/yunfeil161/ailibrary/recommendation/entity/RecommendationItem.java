package com.yunfeil161.ailibrary.recommendation.entity;

import java.math.BigDecimal;
import lombok.Data;

/** 推荐条目保存生成时的分数与解释；库存和封面在响应时重新读取。 */
@Data
public class RecommendationItem {

  private Long id;
  private Long batchId;
  private Long bookId;
  private Integer rank;
  private BigDecimal score;
  private String reason;
  private String evidenceJson;
}
