package com.yunfeil161.ailibrary.communication.conversation.dto;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会话聚合查询条件；成员范围由Service追加。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationQueryRequest extends PageQuery {
  /** 记录ID。 */
  private Long id;

  /** 对方成员ID。 */
  private Long targetMemberId;

  /** 会话主题或消息标题，最多200字。 */
  private String subject;

  /** 业务状态。 */
  private String status;

  /** 仅查询存在本人未读消息的会话。 */
  private Boolean unreadOnly;

  /** 创建时间下界（包含）。 */
  private LocalDateTime createdFrom;

  /** 创建时间上界（包含）。 */
  private LocalDateTime createdTo;
}
