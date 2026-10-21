package com.yunfeil161.ailibrary.communication.conversation.entity;

import java.time.LocalDateTime;
import lombok.Data;

/** 会话持久化信息及本人可见的查询摘要。 */
@Data
public class Conversation {
  /** 记录ID。 */
  private Long id;

  /** 最初创建会话的成员ID。 */
  private Long initiatorMemberId;

  /** 排序后较小的参与者ID。 */
  private Long participantOneId;

  /** 排序后较大的参与者ID。 */
  private Long participantTwoId;

  /** 会话主题或消息标题，最多200字。 */
  private String subject;

  /** 业务状态。 */
  private String status;

  /** 当前会话最大消息序号。 */
  private Long lastSequence;

  /** 最近一条消息ID。 */
  private Long lastMessageId;

  /** 最近发送时间。 */
  private LocalDateTime lastMessageAt;

  /** 会话资料更新的乐观锁版本。 */
  private Long version;

  /** 创建时间。 */
  private LocalDateTime createdAt;

  /** 最近更新时间。 */
  private LocalDateTime updatedAt;

  /** 对方成员ID。 */
  private Long targetMemberId;

  /** 对方成员名称。 */
  private String targetName;

  /** 最近消息的前100字。 */
  private String lastMessagePreview;

  /** 当前成员在此会话的未读数。 */
  private Long unreadCount;
}
