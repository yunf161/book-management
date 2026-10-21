package com.yunfeil161.ailibrary.communication.message.dto;

import java.util.List;
import lombok.Data;

/** 一次发送操作；重试复用clientMessageId。 */
@Data
public class MessageSendRequest {
  /** 所属会话ID；广播消息为空。 */
  private Long conversationId;

  /** 广播收件人ID列表，最多50人，服务端去重。 */
  private List<Long> recipientIds;

  /** 会话主题或消息标题，最多200字。 */
  private String subject;

  /** 消息正文，最多4000字。 */
  private String body;

  /** 前端为一次发送生成的UUID；失败重试必须保持不变。 */
  private String clientMessageId;
}
