package com.yunfeil161.ailibrary.communication.event.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 两个队列共享的收件人分发事件。 */
@Data
public class MessageCreatedEvent {
  /** 事件唯一ID，用于重复投递和提醒去重。 */
  private String eventId;

  /** 事件类型，本版本为message.created。 */
  private String eventType;

  /** 事件格式版本。 */
  private Integer schemaVersion;

  /** 对应消息ID。 */
  private Long messageId;

  /** 收件成员ID；只有发件箱允许筛选其他收件人。 */
  private Long recipientMemberId;

  /** 事件产生时间。 */
  private LocalDateTime occurredAt;
}
