package com.yunfeil161.ailibrary.communication.message.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 成员的独立收件及首次已读状态。 */
@Data
public class MessageRecipient {
  /** 记录ID。 */
  private Long id;

  /** 对应消息ID。 */
  private Long messageId;

  /** 收件成员ID；只有发件箱允许筛选其他收件人。 */
  private Long recipientMemberId;

  /** 收件人名称。 */
  private String recipientName;

  /** 本人收件关系的已读时间；空表示未读。 */
  private LocalDateTime readAt;

  /** 创建时间。 */
  private LocalDateTime createdAt;
}
