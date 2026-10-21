package com.yunfeil161.ailibrary.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 逐一路径验证登录边界。
 *
 * <p>这里启用真实的两层JWT拦截器。请求使用合法的HTTP格式但不携带令牌，
 * 确保受保护接口在参数绑定和业务执行前统一返回401。</p>
 */
@SpringBootTest(
  properties = {
    "jwt.interceptor-enabled=true",
    "spring.datasource.url=jdbc:h2:mem:endpoint_authentication_matrix;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
  }
)
@AutoConfigureMockMvc(addFilters = false)
class EndpointAuthenticationMatrixTests {

  @Autowired
  private MockMvc mvc;

  @Test
  void everyProtectedEndpointRejectsAnonymousRequests() throws Exception {
    for (EndpointCase endpoint : protectedEndpoints()) {
      mvc
        .perform(endpoint.request())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value(401));
    }
  }

  @Test
  void publicCatalogQueriesRemainAnonymous() throws Exception {
    mvc.perform(get("/books")).andExpect(status().isOk());
    mvc.perform(get("/book-categories")).andExpect(status().isOk());
  }

  @Test
  void authenticationEntryPointsReachControllerWithoutExistingSession()
    throws Exception {
    // 登录失败信息来自登录业务，证明请求没有被JWT拦截器提前拒绝。
    mvc
      .perform(
        post("/login")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"missing@example.test\",\"password\":\"wrong\"}")
      )
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.msg").value("邮箱或密码错误"));

    // 其他认证入口用无效参数触发业务校验，避免真实发送邮件或写入数据。
    mvc
      .perform(
        post("/login/code")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{\"email\":\"invalid\"}")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post("/login/register")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest());
    mvc
      .perform(
        post("/login/reset-password")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{}")
      )
      .andExpect(status().isBadRequest());
  }

  private List<EndpointCase> protectedEndpoints() {
    return List.of(
      endpoint("POST /logout", post("/logout")),

      endpoint("GET /members", get("/members").param("memberType", "READER")),
      endpoint("POST /members", json(post("/members"))),
      endpoint("PUT /members/{id}", json(put("/members/1"))),
      endpoint("DELETE /members/{id}", delete("/members/1").param("memberType", "READER")),
      endpoint("GET /admin/members", get("/admin/members").param("memberType", "READER")),
      endpoint("POST /admin/members", json(post("/admin/members"))),
      endpoint("PUT /admin/members/{id}", json(put("/admin/members/1"))),
      endpoint("DELETE /admin/members/{id}", delete("/admin/members/1").param("memberType", "READER")),

      endpoint("POST /member-change-applications", json(post("/member-change-applications"))),
      endpoint("GET /member-change-applications", get("/member-change-applications")),
      endpoint("POST /member-change-applications/{id}/cancel", json(post("/member-change-applications/1/cancel"))),
      endpoint("GET /admin/member-change-applications", get("/admin/member-change-applications")),
      endpoint("POST /admin/member-change-applications/{id}/decision", json(post("/admin/member-change-applications/1/decision"))),

      endpoint("GET /admin/departments", get("/admin/departments")),
      endpoint("POST /admin/departments", json(post("/admin/departments"))),
      endpoint("PUT /admin/departments/{id}", json(put("/admin/departments/1"))),
      endpoint("DELETE /admin/departments/{id}", delete("/admin/departments/1")),

      endpoint("GET /admin/permissions", get("/admin/permissions")),
      endpoint("GET /admin/permissions/{id}", get("/admin/permissions/1")),
      endpoint("POST /admin/permissions", json(post("/admin/permissions"))),
      endpoint("PUT /admin/permissions/{id}", json(put("/admin/permissions/1"))),
      endpoint("DELETE /admin/permissions/{id}", delete("/admin/permissions/1")),

      endpoint("POST /admin/book-categories", json(post("/admin/book-categories"))),
      endpoint("PUT /admin/book-categories/{id}", json(put("/admin/book-categories/1"))),
      endpoint("DELETE /admin/book-categories/{id}", delete("/admin/book-categories/1")),
      endpoint("POST /admin/books", json(post("/admin/books"))),
      endpoint("PUT /admin/books/{id}", json(put("/admin/books/1"))),
      endpoint("DELETE /admin/books/{id}", delete("/admin/books/1")),
      endpoint("GET /admin/book-copies", get("/admin/book-copies")),
      endpoint("POST /admin/books/{id}/copies", json(post("/admin/books/1/copies"))),
      endpoint("PUT /admin/book-copies/{id}", json(put("/admin/book-copies/1"))),
      endpoint("DELETE /admin/book-copies/{id}", delete("/admin/book-copies/1")),

      endpoint("POST /borrow-applications", json(post("/borrow-applications"))),
      endpoint("GET /borrow-applications", get("/borrow-applications")),
      endpoint("POST /borrow-applications/{id}/cancel", json(post("/borrow-applications/1/cancel"))),
      endpoint("GET /admin/borrow-applications", get("/admin/borrow-applications")),
      endpoint("POST /admin/borrow-applications/{id}/approve", json(post("/admin/borrow-applications/1/approve"))),
      endpoint("POST /admin/borrow-applications/{id}/reject", json(post("/admin/borrow-applications/1/reject"))),
      endpoint("GET /borrow-records", get("/borrow-records")),
      endpoint("GET /admin/borrow-records", get("/admin/borrow-records")),
      endpoint("POST /admin/borrow-records/{id}/return", json(post("/admin/borrow-records/1/return"))),
      endpoint("POST /admin/book-copies/{barcode}/return", json(post("/admin/book-copies/UNKNOWN/return"))),

      endpoint("POST /reading-plans", json(post("/reading-plans"))),
      endpoint("GET /reading-plans", get("/reading-plans")),
      endpoint("PUT /reading-plans", json(put("/reading-plans"))),
      endpoint("DELETE /reading-plans", delete("/reading-plans").param("expectedVersion", "0")),
      endpoint("POST /reading-plans/items", json(post("/reading-plans/items"))),
      endpoint("GET /reading-plans/items", get("/reading-plans/items")),
      endpoint("PUT /reading-plans/items/{bookId}", json(put("/reading-plans/items/1"))),
      endpoint("DELETE /reading-plans/items/{bookId}", delete("/reading-plans/items/1").param("expectedVersion", "0").param("expectedPlanVersion", "0")),

      endpoint("POST /admin/reading-plans", json(post("/admin/reading-plans"))),
      endpoint("GET /admin/reading-plans", get("/admin/reading-plans")),
      endpoint("PUT /admin/reading-plans/{memberId}", json(put("/admin/reading-plans/1"))),
      endpoint("DELETE /admin/reading-plans/{memberId}", delete("/admin/reading-plans/1").param("expectedVersion", "0")),
      endpoint("POST /admin/reading-plans/{memberId}/items", json(post("/admin/reading-plans/1/items"))),
      endpoint("GET /admin/reading-plans/items", get("/admin/reading-plans/items")),
      endpoint("PUT /admin/reading-plans/{memberId}/items/{bookId}", json(put("/admin/reading-plans/1/items/1"))),
      endpoint("DELETE /admin/reading-plans/{memberId}/items/{bookId}", delete("/admin/reading-plans/1/items/1").param("expectedVersion", "0").param("expectedPlanVersion", "0"))
    );
  }

  private MockHttpServletRequestBuilder json(
    MockHttpServletRequestBuilder request
  ) {
    return request.contentType(MediaType.APPLICATION_JSON).content("{}");
  }

  private EndpointCase endpoint(
    String name,
    MockHttpServletRequestBuilder request
  ) {
    return new EndpointCase(name, request);
  }

  private record EndpointCase(
    String name,
    MockHttpServletRequestBuilder request
  ) {}
}
