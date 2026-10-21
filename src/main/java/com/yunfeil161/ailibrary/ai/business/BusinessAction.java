package com.yunfeil161.ailibrary.ai.business;

import java.time.LocalDateTime;
import lombok.Data;

/** 持久化的待确认操作。业务令牌和内部AI凭据不得写入该表。 */
@Data
public class BusinessAction {

  private Long id;
  private String actionId;
  private Long memberId;
  private String skillCode;
  private String title;
  private String requestJson;
  private String requestHash;
  private String state;
  private Long version;
  private Integer resultCode;
  private String resultJson;
  private LocalDateTime expiresAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
