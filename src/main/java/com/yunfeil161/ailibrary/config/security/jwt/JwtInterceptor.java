package com.yunfeil161.ailibrary.config.security.jwt;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 受保护接口的登录要求拦截器。
 *
 * <p>JWT 和 Redis 已由第一层拦截器校验，本层只信任当前请求中由服务器写入的
 * {@link LoginSession}，不再重复解析令牌或查询 Redis。</p>
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
    HttpServletRequest request,
    HttpServletResponse response,
    Object handler
  ) {
    log.info(
      "检查接口登录要求：{} {}",
      request.getMethod(),
      request.getRequestURI()
    );

    if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
      return true;
    }

    // 图书目录与分类是公开检索能力；写接口和馆藏副本接口仍需登录。
    if (isPublicCatalogQuery(request)) {
      return true;
    }

    Object authenticatedSession = request.getAttribute(
      TokenRefreshInterceptor.AUTHENTICATED_SESSION_ATTRIBUTE
    );
    if (!(authenticatedSession instanceof LoginSession session)) {
      throw new UnauthorizedException("登录状态已失效，请重新登录");
    }

    LoginUserContext.set(session);
    return true;
  }

  /** 仅放行GET目录查询，路径边界可避免误放行相似的管理接口。 */
  private boolean isPublicCatalogQuery(HttpServletRequest request) {
    if (!"GET".equalsIgnoreCase(request.getMethod())) return false;
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (
      contextPath != null &&
      !contextPath.isEmpty() &&
      uri.startsWith(contextPath)
    ) {
      uri = uri.substring(contextPath.length());
    }
    return (
      "/books".equals(uri) ||
      "/policies/current".equals(uri) ||
      uri.startsWith("/books/") ||
      "/book-categories".equals(uri) ||
      uri.startsWith("/book-categories/")
    );
  }

  @Override
  public void afterCompletion(
    HttpServletRequest request,
    HttpServletResponse response,
    Object handler,
    Exception exception
  ) {
    LoginUserContext.clear();
  }
}
