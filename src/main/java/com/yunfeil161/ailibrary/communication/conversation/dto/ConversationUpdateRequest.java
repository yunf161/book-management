package com.yunfeil161.ailibrary.communication.conversation.dto;

import lombok.Data;

/** 会话状态和主题更新，必须携带版本。 */
@Data
public class ConversationUpdateRequest {
  /** 会话主题或消息标题，最多200字。 */
  private String subject;

  /** 业务状态。 */
  private String status;

  /** 客户端读取的版本，用于拒绝过期更新。 */
  private Long expectedVersion;
}
