package com.yunfeil161.ailibrary.recommendation.dto;

import com.yunfeil161.ailibrary.book.catalog.entity.BookDetailVO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 仅返回当前推荐；无当前批次时 items 为空，由用户显式生成。 */
@Data
public class RecommendationResponse {

  private Long batchId;
  /** 当前偏好开关；false表示仅使用热门、新书、库存及分类多样性。 */
  private boolean personalizationEnabled;
  private String source;
  private String algorithmVersion;
  private LocalDateTime generatedAt;
  private LocalDateTime expiresAt;
  private List<Entry> items = new ArrayList<>();

  @Data
  public static class Entry {

    private Long itemId;
    private Integer rank;
    private BigDecimal score;
    private String reason;
    private BookDetailVO book;
  }
}
