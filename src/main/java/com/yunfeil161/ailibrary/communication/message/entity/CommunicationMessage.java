package com.yunfeil161.ailibrary.communication.message.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** 消息查询对象；收件名单只向发送者返回。 */
@Data
public class CommunicationMessage {
  /** CHAT人工消息、SYSTEM业务通知；旧聊天数据默认CHAT。 */
  private String messageType;

  /** 关联业务类型及记录ID，跳转后仍由目标接口重新鉴权。 */
  private String businessType;

  private Long businessId;

  /** 系统通知唯一键，不接收前端传入。 */
  @com.fasterxml.jackson.annotation.JsonIgnore private String notificationKey;

  /** 记录ID。 */
  private Long id;

  /** 所属会话ID；广播消息为空。 */
  private Long conversationId;

  /** 会话内递增序号，由锁定的会话行分配。 */
  private Long sequence;

  /** 发送成员ID。 */
  private Long senderMemberId;

  /** 发送人名称，查询关联member取得。 */
  private String senderName;

  /** DIRECT单聊或MULTICAST广播。 */
  private String sendType;

  /** 会话主题或消息标题，最多200字。 */
  private String subject;

  /** 消息正文，最多4000字。 */
  private String body;

  /** 前端为一次发送生成的UUID；失败重试必须保持不变。 */
  private String clientMessageId;

  /** 仅用于服务端幂等校验，不输出给HTTP客户端。 */
  @com.fasterxml.jackson.annotation.JsonIgnore private String requestHash;

  /** 去重后的收件人数。 */
  private Integer recipientCount;

  /** 创建时间。 */
  private LocalDateTime createdAt;

  /** 本人收件关系的已读时间；空表示未读。 */
  private LocalDateTime readAt;

  /** 仅向发送人返回收件名单。 */
  private List<MessageRecipient> recipients;
}
