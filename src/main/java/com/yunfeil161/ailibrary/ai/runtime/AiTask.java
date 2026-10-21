package com.yunfeil161.ailibrary.ai.runtime;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 持久任务及计量；未知用量保持null，不能把估算或缺失值冒充实际账单。 */
@Data
public class AiTask {

  private Long id;
  private String taskId;
  private Long memberId;
  private String type;
  private String question;
  private String state;
  private Integer attempt;
  private String parentTaskId;
  private String resultJson;
  private String errorCode;
  private Long promptTokens;
  private Long completionTokens;
  private BigDecimal estimatedCost;
  private Long version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
