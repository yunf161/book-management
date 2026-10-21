package com.yunfeil161.ailibrary.audit;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 补记在JWT拦截器就被拒绝的写请求；不解析JWT，不读取凭证或请求正文。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class AuditHttpFailureFilter extends OncePerRequestFilter {

  private final OperationLogWriter writer;

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain chain
  ) throws ServletException, IOException {
    try {
      chain.doFilter(request, response);
    } finally {
      if (
        response.getStatus() >= 400 &&
        request.getAttribute(OperationAuditAspect.class.getName()) == null &&
        java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(
          request.getMethod()
        ) &&
        (request.getRequestURI().startsWith("/admin/") ||
          request.getRequestURI().startsWith("/members") ||
          request.getRequestURI().startsWith("/borrow-applications") ||
          request.getRequestURI().startsWith("/member-change-applications") ||
          request.getRequestURI().startsWith("/reading-plan"))
      ) {
        var event = writer.entry(
          "HTTP_OPERATION",
          null,
          "HTTP_" + request.getMethod()
        );
        // 会话已清理或未通过认证时，不把未知请求伪装成系统定时任务。
        if (event.getActorMemberId() == null) event.setActorKind("ANONYMOUS");
        try {
          writer.failure(event, "HTTP_" + response.getStatus());
        } catch (RuntimeException failure) {
          log.error(
            "HTTP失败审计保存失败：eventId={}",
            event.getEventId(),
            failure
          );
        }
      }
    }
  }
}
