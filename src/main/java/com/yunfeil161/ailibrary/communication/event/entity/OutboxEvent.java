package com.yunfeil161.ailibrary.communication.event.entity;

import lombok.Data;

/** 与消息同事务保存的待发布事件。 */
@Data
public class OutboxEvent {
  /** 记录ID。 */
  private Long id;

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

  /** 序列化事件，两个队列使用同一载荷。 */
  private String payloadJson;

  /** 业务状态。 */
  private String status;

  /** Outbox累计发布尝试次数。 */
  private Integer attemptCount;

  /** 本次领取令牌；旧发布者不能覆盖新租约。 */
  private String leaseToken;
}
