package com.yunfeil161.ailibrary.communication.conversation.dto;

import lombok.Data;

/** 创建会话的字段白名单。 */
@Data
public class ConversationCreateRequest {
  /** 对方成员ID。 */
  private Long targetMemberId;

  /** 会话主题或消息标题，最多200字。 */
  private String subject;
}
