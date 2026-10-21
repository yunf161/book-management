package com.yunfeil161.ailibrary.ai.handoff;

import java.time.LocalDateTime;
import lombok.Data;

/** 经用户确认的交接摘要；不复制提示词、工具原文或私有推理链。 */
@Data
public class AiHandoff {

  private Long id;
  private Long memberId;
  private Long staffMemberId;
  private Long conversationId;
  private String aiConversationId;
  private String summary;
  private String status;
  private Integer activeSlot;
  private Long version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
