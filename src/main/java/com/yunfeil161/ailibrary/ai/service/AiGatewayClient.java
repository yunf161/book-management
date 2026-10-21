package com.yunfeil161.ailibrary.ai.service;

import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 内部AI连接层。禁止重定向，凭据放HTTP头中，不进入模型提示词。 */
@Component
public class AiGatewayClient {

  @Value("${library.ai.url:http://127.0.0.1:8001}")
  private String url;

  @Value("${library.recommendation.ai-token:}")
  private String secret;

  @Value("${library.ai.read-timeout-millis:90000}")
  private int readTimeout;

  public record Stream(
    HttpURLConnection connection,
    InputStream body
  ) implements AutoCloseable {
    @Override
    public void close() throws IOException {
      try {
        body.close();
      } finally {
        connection.disconnect();
      }
    }
  }

  public Stream chat(
    Long memberId,
    String token,
    String question,
    String conversationId
  ) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("memberId", memberId);
    input.put("question", question);
    if (conversationId != null) input.put("conversationId", conversationId);
    return open("/internal/assistant/chat", "POST", memberId, token, input);
  }

  public Object conversation(Long memberId, String id, boolean delete) {
    try (
      var stream = open(
        "/internal/assistant/conversations/" + id,
        delete ? "DELETE" : "GET",
        memberId,
        null,
        null
      )
    ) {
      byte[] body = stream.body().readNBytes(100001);
      if (body.length > 100000) throw new IOException("会话响应过长");
      return JsonUtils.fromJson(
        new String(body, StandardCharsets.UTF_8),
        Map.class
      );
    } catch (IOException exception) {
      throw new ServiceUnavailableException("AI会话服务暂不可用");
    }
  }

  public Stream business(
    Long memberId,
    String token,
    String question,
    String conversationId
  ) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("memberId", memberId);
    input.put("question", question);
    input.put("task", "BUSINESS");
    if (conversationId != null) input.put("conversationId", conversationId);
    return open("/internal/assistant/chat", "POST", memberId, token, input);
  }

  private Stream open(
    String path,
    String method,
    Long memberId,
    String token,
    Object input
  ) {
    if (
      secret == null || secret.isBlank()
    ) throw new ServiceUnavailableException("AI内部服务尚未配置");
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) URI.create(url + path)
        .toURL()
        .openConnection(Proxy.NO_PROXY);
      connection.setInstanceFollowRedirects(false);
      connection.setConnectTimeout(1000);
      connection.setReadTimeout(readTimeout);
      connection.setRequestMethod(method);
      connection.setRequestProperty("Authorization", "Bearer " + secret);
      connection.setRequestProperty("X-Member-Id", memberId.toString());
      if (token != null) connection.setRequestProperty(
        "X-Library-Token",
        token
      );
      if (input != null) {
        byte[] bytes = JsonUtils.toJson(input).getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (var output = connection.getOutputStream()) {
          output.write(bytes);
        }
      }
      int status = connection.getResponseCode();
      if (status != 200) {
        boolean sessionExpired = false;
        if (status == 401 && connection.getErrorStream() != null) {
          try (var error = connection.getErrorStream()) {
            var data = JsonUtils.fromJson(
              new String(error.readNBytes(4096), StandardCharsets.UTF_8),
              Map.class
            );
            sessionExpired = Boolean.TRUE.equals(data.get("sessionExpired"));
          } catch (Exception ignored) {
            // 内部凭据配置错误不应误注销前端的有效登录。
          }
        }
        connection.disconnect();
        if (sessionExpired) throw new UnauthorizedException(
          "登录已失效，请重新登录"
        );
        switch (status) {
          case 403 -> throw new ForbiddenException(
            "当前账号无权执行本轮AI工具查询"
          );
          case 404 -> throw new NotFoundException(
            "AI会话或所查询的业务记录不存在"
          );
          case 409 -> throw new ConflictException(
            "该AI会话正在处理，请稍后重试"
          );
          default -> throw new ServiceUnavailableException(
            "AI暂不可用，请稍后重试"
          );
        }
      }
      return new Stream(connection, connection.getInputStream());
    } catch (IOException exception) {
      if (connection != null) connection.disconnect();
      throw new ServiceUnavailableException("无法连接AI服务或回答超时");
    }
  }
}
