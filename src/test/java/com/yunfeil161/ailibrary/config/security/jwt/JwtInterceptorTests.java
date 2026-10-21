package com.yunfeil161.ailibrary.config.security.jwt;

import static org.junit.jupiter.api.Assertions.*;

import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** 验证登录拦截器只匿名放行公开图书目录查询。 */
class JwtInterceptorTests {

  private final JwtInterceptor interceptor = new JwtInterceptor();

  @Test
  void anonymousCatalogGetIsAllowed() {
    assertTrue(preHandle("GET", "/books"));
    assertTrue(preHandle("GET", "/book-categories"));
  }

  @Test
  void adminAndCopyRequestsStillRequireLogin() {
    assertThrows(UnauthorizedException.class, () ->
      preHandle("POST", "/admin/books")
    );
    assertThrows(UnauthorizedException.class, () ->
      preHandle("GET", "/admin/book-copies")
    );
    assertThrows(UnauthorizedException.class, () ->
      preHandle("POST", "/books")
    );
  }

  private boolean preHandle(String method, String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    return interceptor.preHandle(
      request,
      new MockHttpServletResponse(),
      new Object()
    );
  }
}
