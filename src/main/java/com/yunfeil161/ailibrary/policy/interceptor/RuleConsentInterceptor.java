package com.yunfeil161.ailibrary.policy.interceptor;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.policy.service.PolicyState;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 在登录校验后限制未接受当前规则的会话，公开检索和须知决策仍可访问。 */
@Component
@RequiredArgsConstructor
public class RuleConsentInterceptor implements HandlerInterceptor {

  private final PolicyState policies;

  @Override
  public boolean preHandle(
    HttpServletRequest request,
    HttpServletResponse response,
    Object handler
  ) {
    String path = request
      .getRequestURI()
      .substring(request.getContextPath().length());
    // 已登录但尚未确认的成员也保留匿名用户具有的公开检索能力。
    if (
      "GET".equals(request.getMethod()) &&
      (path.equals("/books") ||
        path.startsWith("/books/") ||
        path.equals("/book-categories") ||
        path.startsWith("/book-categories/"))
    ) return true;
    if (
      "OPTIONS".equals(request.getMethod()) ||
      path.startsWith("/login") ||
      path.startsWith("/me/borrowing-rules") ||
      path.startsWith("/ws/") ||
      path.equals("/policies/current") ||
      path.equals("/error")
    ) return true;
    var session = LoginUserContext.getNullable();
    if (session != null && policies.requiresConsent(session.getMemberId())) {
      response.setHeader("X-Error-Code", "RULE_CONSENT_REQUIRED");
      throw new ForbiddenException("请先接受当前借阅须知");
    }
    return true;
  }
}
