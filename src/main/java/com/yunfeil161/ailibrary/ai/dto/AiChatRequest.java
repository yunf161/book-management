package com.yunfeil161.ailibrary.ai.dto;

/** 新会话不传conversationId；身份和业务令牌始终由Java从可信登录请求取得。 */
public record AiChatRequest(
  String question,
  String conversationId,
  String task
) {
  public AiChatRequest(String question, String conversationId) {
    this(question, conversationId, null);
  }
}
