package com.yunfeil161.ailibrary.communication.websocket;

import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.communication.common.security.CommunicationAccess;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/** 一次性短期票据避免JWT出现在WebSocket URL；不接受URL中的成员ID。 */
@Configuration
@EnableWebSocket
@RestController
@RequiredArgsConstructor
public class SocketConfiguration implements WebSocketConfigurer, HandshakeInterceptor {
  private final NotificationSocket handler;
  private final CommunicationAccess access;
  private final LoginSessionService sessions;
  private final StringRedisTemplate redis;
  private final com.yunfeil161.ailibrary.policy.service.PolicyState policies;
  private final com.yunfeil161.ailibrary.config.security.role.service.RoleQueryService roles;

  @Value("${library.communication.allowed-origins:http://localhost:5173,http://localhost:8080}")
  private String[] origins;

  @PostMapping("/ws/tickets")
  public Result ticket() {
    LoginSession identity = com.yunfeil161.ailibrary.auth.session.LoginUserContext.get();
    if (identity == null) throw new com.yunfeil161.ailibrary.common.exception.UnauthorizedException("请先登录");
    access.active(identity.getMemberId());
    if (!policies.requiresConsent(identity.getMemberId())) access.require("websocket:connect:self");
    String ticket = UUID.randomUUID().toString();
    redis
        .opsForValue()
        .set("communication:ticket:" + ticket, JsonUtils.toJson(identity), Duration.ofSeconds(60));
    return Result.success(Map.of("ticket", ticket, "expiresIn", 60, "path", "/ws/events"));
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/events").addInterceptors(this).setAllowedOrigins(origins);
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {
    String ticket =
        UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
    if (ticket == null || !ticket.matches("[a-fA-F0-9-]{36}")) return false;
    String value = redis.opsForValue().getAndDelete("communication:ticket:" + ticket);
    if (value == null) return false;
    try {
      LoginSession identity = JsonUtils.fromJson(value, LoginSession.class);
      LoginSession live = sessions.find(identity.getJti());
      if (live == null || !identity.getMemberId().equals(live.getMemberId())) return false;
      roles.requireActiveRole(access.active(live.getMemberId()).getRoleId());
      // 角色授权必须在握手时仍有效，避免票据签发后停用角色仍可连接。
      com.yunfeil161.ailibrary.auth.session.LoginUserContext.set(live);
      if (!policies.requiresConsent(live.getMemberId())) access.require("websocket:connect:self");
      attributes.put("identity", live);
      return true;
    } catch (RuntimeException e) {
      return false;
    } finally {
      com.yunfeil161.ailibrary.auth.session.LoginUserContext.clear();
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler handler,
      Exception exception) {
    // 握手没有使用跨请求的ThreadLocal上下文。
  }
}
