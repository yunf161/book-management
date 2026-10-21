package com.yunfeil161.ailibrary.communication.message.dto;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本人消息聚合查询；增量补拉使用会话sequence。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageQueryRequest extends PageQuery {
  /** 在原聚合查询中筛选人工消息或业务通知。 */
  private String messageType;

  private String businessType;
  private Long businessId;

  /** 记录ID。 */
  private Long id;

  /** 所属会话ID；广播消息为空。 */
  private Long conversationId;

  /** INBOX收件箱、OUTBOX发件箱或ALL，始终限定本人范围。 */
  private String box;

  /** 发送成员ID。 */
  private Long senderMemberId;

  /** 收件成员ID；只有发件箱允许筛选其他收件人。 */
  private Long recipientMemberId;

  /** DIRECT单聊或MULTICAST广播。 */
  private String sendType;

  /** 会话主题或消息标题，最多200字。 */
  private String subject;

  /** 是否已读，仅支持INBOX。 */
  private Boolean read;

  /** 创建时间下界（包含）。 */
  private LocalDateTime createdFrom;

  /** 创建时间上界（包含）。 */
  private LocalDateTime createdTo;

  /** 增量补拉的已知序号，必须同时传conversationId。 */
  private Long afterSequence;
}
