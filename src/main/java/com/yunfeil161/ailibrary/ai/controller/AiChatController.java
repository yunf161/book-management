package com.yunfeil161.ailibrary.ai.controller;

import com.yunfeil161.ailibrary.ai.business.BusinessActionService;
import com.yunfeil161.ailibrary.ai.dto.AiChatRequest;
import com.yunfeil161.ailibrary.ai.service.AiChatService;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.common.exception.*;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import com.yunfeil161.ailibrary.config.security.jwt.JwtUtils;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/** SSE仅传输临时片段；前端必须等done才将本轮视为成功，error或断流应丢弃半轮回答。 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

  private final AiChatService service;
  private final JwtUtils jwt;
  private final com.yunfeil161.ailibrary.ai.runtime.AiUsageService usage;
  private final BusinessActionService business;

  @PostMapping("/ai/chat")
  public void chat(
    @RequestBody AiChatRequest input,
    HttpServletRequest request,
    HttpServletResponse response
  ) throws IOException {
    String authorization = request.getHeader("Authorization");
    String token = response.getHeader("X-Refreshed-Token");
    if (
      token == null &&
      authorization != null &&
      authorization.startsWith("Bearer ")
    ) token = authorization.substring(7).trim();
    // 打开上游前完成鉴权、参数校验及首轮工具查询，尚未写出SSE时仍使用全局异常返回HTTP错误。
    try (var upstream = service.chat(input, token)) {
      String refreshed = upstream
        .connection()
        .getHeaderField("X-Refreshed-Token");
      if (refreshed != null && !refreshed.equals(token)) {
        try {
          var claims = jwt.parseToken(refreshed);
          if (
            !Objects.equals(
              String.valueOf(claims.get("memberId")),
              LoginUserContext.get().getMemberId().toString()
            )
          ) throw new IllegalArgumentException();
          response.setHeader("X-Refreshed-Token", refreshed);
        } catch (RuntimeException exception) {
          throw new com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException(
            "AI会话轮换结果异常，请重新登录后重试"
          );
        }
      }
      response.setContentType("text/event-stream;charset=UTF-8");
      response.setHeader("Cache-Control", "no-store");
      response.setHeader("X-Accel-Buffering", "no");
      var writer = response.getWriter();
      boolean completed = false;
      String usageEventId = java.util.UUID.randomUUID().toString();
      long deadline =
        System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(120);
      try {
        var reader = new InputStreamReader(
          upstream.body(),
          StandardCharsets.UTF_8
        );
        String event = null;
        int total = 0;
        while (System.nanoTime() < deadline) {
          String line = boundedLine(reader);
          if (line == null) break;
          total += line.length();
          if (total > 150000) throw new IOException("AI响应过长");
          if (line.startsWith("event: ")) event = line.substring(7);
          else if (line.startsWith("data: ")) {
            // 接管可能发生在模型生成期间，每个事件转发前再次确认，避免接管后继续停留在AI回答。
            service.requireAutomated(input.conversationId());
            if (
              !Set.of(
                "meta",
                "delta",
                "sources",
                "done",
                "error",
                "proposal",
                "usage"
              ).contains(event == null ? "" : event)
            ) throw new IOException("AI事件格式错误");
            Map<?, ?> value = JsonUtils.fromJson(line.substring(6), Map.class);
            if (
              "usage".equals(event) &&
              value.get("calls") instanceof java.util.List<?> calls
            ) usage.record(
              usageEventId,
              LoginUserContext.get().getMemberId(),
              calls
            );
            if ("proposal".equals(event)) {
              if (
                !"BUSINESS".equals(input.task()) ||
                !value.keySet().equals(Set.of("skill", "arguments")) ||
                !(value.get("skill") instanceof String skill) ||
                !(value.get("arguments") instanceof Map<?, ?> arguments)
              ) throw new IOException("业务草稿格式错误");
              value = business.preview(
                new BusinessActionService.Preview(
                  skill,
                  (Map<String, Object>) arguments
                )
              );
              event = "action";
            }
            writer.write(
              "event: " + event + "\ndata: " + JsonUtils.toJson(value) + "\n\n"
            );
            writer.flush();
            if (writer.checkError()) return; // 浏览器取消时关闭上游，不继续占用连接。
            if ("done".equals(event) || "error".equals(event)) {
              completed = true;
              break;
            }
            event = null;
          }
        }
        if (!completed) throw new IOException("AI回答提前中断");
      } catch (Exception exception) {
        log.warn("AI流式回答中断：{}", exception.getClass().getSimpleName());
        // 响应头已发送后，仍用SSE表达真实权限/参数错误，不能误报成模型故障。
        int errorCode =
          exception instanceof ForbiddenException
            ? 403
            : exception instanceof UnauthorizedException
              ? 401
              : exception instanceof WrongFormatException
                ? 400
                : exception instanceof ConflictException
                  ? 409
                  : exception instanceof NotFoundException
                    ? 404
                    : 503;
        writer.write(
          "event: error\ndata: " +
            JsonUtils.toJson(
              Map.of(
                "code",
                errorCode,
                "message",
                "AI回答中断，请重试；本轮内容未确认为有效答案"
              )
            ) +
            "\n\n"
        );
        writer.flush();
      }
    }
  }

  /** 单行有界读取，避免异常内部服务用超长SSE行耗尽内存。 */
  private String boundedLine(Reader reader) throws IOException {
    var line = new StringBuilder();
    int next;
    while ((next = reader.read()) != -1) {
      if (next == '\n') return line.toString();
      if (next != '\r') line.append((char) next);
      if (line.length() > 65000) throw new IOException("AI事件过长");
    }
    return line.isEmpty() ? null : line.toString();
  }

  @GetMapping("/ai/conversations/{id}")
  public Result conversation(@PathVariable String id) {
    return Result.success(service.conversation(id, false));
  }

  @DeleteMapping("/ai/conversations/{id}")
  public Result clear(@PathVariable String id) {
    return Result.success(service.conversation(id, true));
  }
}
