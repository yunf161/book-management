package com.yunfeil161.ailibrary.communication.websocket;

import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.common.cache.UnreadIndex;
import com.yunfeil161.ailibrary.communication.common.security.CommunicationAccess;
import com.yunfeil161.ailibrary.communication.event.entity.MessageCreatedEvent;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** 连接只发送轻量提醒，消息正文仍通过有范围限制的HTTP接口查询。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationSocket extends TextWebSocketHandler {
  private final LoginSessionService sessions;
  private final CommunicationAccess access;
  private final PermissionQueryService permissions;
  private final UnreadIndex index;
  private final com.yunfeil161.ailibrary.policy.service.PolicyState policyState;
  private final com.yunfeil161.ailibrary.config.security.role.service.RoleQueryService roles;
  private final Map<String, WebSocketSession> connections = new ConcurrentHashMap<>();
  private final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession socket) {
    connections.put(socket.getId(), socket);
    lastHeartbeat.put(socket.getId(), System.currentTimeMillis());
    LoginSession identity = (LoginSession) socket.getAttributes().get("identity");
    try {
      if (policyState.requiresConsent(identity.getMemberId())) return;
      index.rebuild(identity.getMemberId(), java.util.List.of());
    } catch (RuntimeException e) {
      // 索引重建失败不丢消息，连接建立后客户端仍可用HTTP查询数据库。
      log.warn("上线未读索引重建失败：memberId={}", identity.getMemberId(), e);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession socket, CloseStatus status) {
    connections.remove(socket.getId());
    lastHeartbeat.remove(socket.getId());
  }

  @Override
  protected void handleTextMessage(WebSocketSession socket, TextMessage message) throws Exception {
    if (!valid(socket)) {
      socket.close(CloseStatus.POLICY_VIOLATION);
      return;
    }
    // 心跳也重新校验Redis会话，登出或账号停用后不继续保留访问能力。
    if ("ping".equals(message.getPayload())) {
      lastHeartbeat.put(socket.getId(), System.currentTimeMillis());
      synchronized (socket) {
        socket.sendMessage(new TextMessage("pong"));
      }
    } else {
      socket.close(CloseStatus.NOT_ACCEPTABLE);
    }
  }

  private boolean valid(WebSocketSession socket) {
    LoginSession identity = (LoginSession) socket.getAttributes().get("identity");
    try {
      LoginSession current = sessions.find(identity.getJti());
      if (current == null || !identity.getMemberId().equals(current.getMemberId())) return false;
      var member = access.active(current.getMemberId());
      roles.requireActiveRole(member.getRoleId());
      return policyState.requiresConsent(member) || permissions.hasPermission(member.getRoleId(), "websocket:connect:self");
    } catch (RuntimeException e) {
      return false;
    }
  }

  public boolean online(Long memberId) {
    return connections.values().stream()
        .anyMatch(
            socket ->
                socket.isOpen()
                    && memberId.equals(
                        ((LoginSession) socket.getAttributes().get("identity")).getMemberId())
                    && valid(socket)
                    && !policyState.requiresConsent(memberId));
  }

  /** 即使没有新消息，过期会话和超过90秒无心跳的连接也会被回收。 */
  @Scheduled(fixedDelay = 30000)
  public void sweep() {
    for (WebSocketSession socket : connections.values()) {
      if (!valid(socket)
          || System.currentTimeMillis() - lastHeartbeat.getOrDefault(socket.getId(), 0L) > 90000) {
        try {
          socket.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
          log.warn("关闭失效WebSocket失败：sessionId={}", socket.getId(), e);
        } finally {
          connections.remove(socket.getId());
          lastHeartbeat.remove(socket.getId());
        }
      }
    }
  }

  /** 多设备逐个通知；离线无须重试，客户端上线后查询数据库补齐。 */
  public void notify(MessageCreatedEvent event) throws IOException {
    for (WebSocketSession socket : connections.values()) {
      LoginSession identity = (LoginSession) socket.getAttributes().get("identity");
      if (!event.getRecipientMemberId().equals(identity.getMemberId()) || !socket.isOpen())
        continue;
      if (!valid(socket)) {
        socket.close(CloseStatus.POLICY_VIOLATION);
        continue;
      }
      synchronized (socket) {
        if (policyState.requiresConsent(identity.getMemberId())) continue;
        socket.sendMessage(new TextMessage(JsonUtils.toJson(event)));
      }
    }
  }

  /** 政策更新只推送版本标识，受限会话也可接收，不携带业务数据。 */
  public void rulesUpdated(Object event) throws IOException {
    for (WebSocketSession socket : connections.values()) {
      if (!socket.isOpen() || !valid(socket)) continue;
      synchronized (socket) {
        socket.sendMessage(new TextMessage(JsonUtils.toJson(event)));
      }
    }
  }

  /** 拒绝规则后立即关闭该成员所有设备连接。 */
  public void closeMember(Long memberId) {
    for (WebSocketSession socket : connections.values()) {
      LoginSession identity = (LoginSession) socket.getAttributes().get("identity");
      if (!memberId.equals(identity.getMemberId())) continue;
      try { socket.close(CloseStatus.POLICY_VIOLATION); }
      catch (IOException e) { log.warn("关闭成员连接失败：memberId={}", memberId, e); }
    }
  }
}
