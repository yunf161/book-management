package com.yunfeil161.ailibrary.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 逐一验证业务接口的角色权限边界和关键数据范围边界。
 *
 * <p>本类关闭JWT拦截器并直接设置服务端登录上下文，专门验证Controller和
 * Service不能因为路径名称、请求参数或成员类型而绕过role_permission。</p>
 */
@SpringBootTest(
  properties =
    "spring.datasource.url=jdbc:h2:mem:endpoint_authorization_matrix;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class EndpointAuthorizationMatrixTests {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JdbcTemplate jdbc;

  @AfterEach
  void clearContext() {
    LoginUserContext.clear();
  }

  @Test
  void aiRoleCannotEnterAnyHumanOrManagementBusinessEndpoint()
    throws Exception {
    login("AI_LIBRARIAN", MemberType.AI_LIBRARIAN, 1L);

    for (EndpointCase endpoint : permissionProtectedEndpoints()) {
      mvc
        .perform(endpoint.request())
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(403));
    }
  }

  @Test
  void readerSelfScopeCannotCrossIntoAdministrativeBorrowEndpoints()
    throws Exception {
    login("READER", MemberType.READER, 1L);

    List<MockHttpServletRequestBuilder> requests = List.of(
      get("/admin/borrow-applications"),
      json(post("/admin/borrow-applications/1/approve")),
      json(post("/admin/borrow-applications/1/reject")),
      get("/admin/borrow-records"),
      json(post("/admin/borrow-records/1/return")),
      json(post("/admin/book-copies/UNKNOWN/return"))
    );
    for (MockHttpServletRequestBuilder request : requests) {
      mvc.perform(request).andExpect(status().isForbidden());
    }
  }

  @Test
  void readerCannotUseManagementEndpointsEvenThroughSharedMemberPaths()
    throws Exception {
    login("READER", MemberType.READER, 1L);

    List<MockHttpServletRequestBuilder> requests = List.of(
      json(post("/members")),
      delete("/members/2").param("memberType", "READER"),
      json(post("/admin/members")),
      delete("/admin/members/2").param("memberType", "READER"),
      get("/admin/departments"),
      get("/admin/permissions"),
      get("/admin/book-copies"),
      get("/admin/reading-plans")
    );
    for (MockHttpServletRequestBuilder request : requests) {
      mvc.perform(request).andExpect(status().isForbidden());
    }
  }

  @Test
  void readerAndLibrarianCannotModifyAnotherMemberWithSelfScope()
    throws Exception {
    login("READER", MemberType.READER, 1L);
    mvc
      .perform(json(put("/members/2")))
      .andExpect(status().isForbidden());

    login("LIBRARIAN", MemberType.EMPLOYEE, 2L);
    mvc
      .perform(json(put("/admin/members/1")))
      .andExpect(status().isForbidden());
  }

  @Test
  void librarianQueryPermissionDoesNotImplyDepartmentOrPermissionWrites()
    throws Exception {
    login("LIBRARIAN", MemberType.EMPLOYEE, 2L);

    List<MockHttpServletRequestBuilder> requests = List.of(
      json(post("/admin/departments")),
      json(put("/admin/departments/1")),
      delete("/admin/departments/1"),
      get("/admin/permissions"),
      json(post("/admin/permissions")),
      json(put("/admin/permissions/1")),
      delete("/admin/permissions/1"),
      json(post("/admin/members")),
      delete("/admin/members/1").param("memberType", "READER")
    );
    for (MockHttpServletRequestBuilder request : requests) {
      mvc.perform(request).andExpect(status().isForbidden());
    }
  }

  @Test
  void explicitPermissionWithInsufficientScopeIsStillRejected()
    throws Exception {
    Long adminRole = role("ADMIN");
    jdbc.update(
      "update role_permission set data_scope='SELF' where role_id=? and permission_id=(select id from permission where code='permission:query')",
      adminRole
    );
    login("ADMIN", MemberType.EMPLOYEE, 3L);

    mvc
      .perform(get("/admin/permissions"))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.msg").value("数据权限范围不足"));
  }

  @Test
  void forgedMemberTypeAndRoleCodeCannotReplaceRoleIdAuthorization()
    throws Exception {
    // 故意伪造成员类型和角色编码，但roleId仍指向READER。
    LoginUserContext.set(
      new LoginSession(
        "forged-session",
        1L,
        role("READER"),
        MemberType.EMPLOYEE,
        "ADMIN",
        "forged@example.test",
        "伪造会话"
      )
    );

    mvc
      .perform(get("/admin/permissions"))
      .andExpect(status().isForbidden());
    mvc
      .perform(json(post("/admin/books")))
      .andExpect(status().isForbidden());
  }

  @Test
  void disablingRoleRevokesEndpointAccessWithoutDeletingSession()
    throws Exception {
    Long librarianRole = role("LIBRARIAN");
    login("LIBRARIAN", MemberType.EMPLOYEE, 2L);
    jdbc.update(
      "update `role` set status='DISABLED' where id=?",
      librarianRole
    );

    mvc
      .perform(get("/admin/book-copies"))
      .andExpect(status().isForbidden());
  }

  @Test
  void selfScopedMemberQueryOverridesForgedTargetMemberId()
    throws Exception {
    Long readerRole = role("READER");
    jdbc.update(
      "insert into member(member_type,role_id,name,email,password_hash,account_status,qualification_status,version) values('READER',?,'权限本人','scope.self@example.test','hash','ACTIVE','NOT_APPLIED',0)",
      readerRole
    );
    jdbc.update(
      "insert into member(member_type,role_id,name,email,password_hash,account_status,qualification_status,version) values('READER',?,'权限他人','scope.other@example.test','hash','ACTIVE','NOT_APPLIED',0)",
      readerRole
    );
    Long selfId = jdbc.queryForObject(
      "select id from member where email='scope.self@example.test'",
      Long.class
    );
    Long otherId = jdbc.queryForObject(
      "select id from member where email='scope.other@example.test'",
      Long.class
    );
    login("READER", MemberType.READER, selfId);

    mvc
      .perform(
        get("/admin/members")
          .param("memberType", "READER")
          .param("id", String.valueOf(otherId))
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data.total").value(1))
      .andExpect(jsonPath("$.data.rows[0].id").value(selfId));
  }

  private List<EndpointCase> permissionProtectedEndpoints() {
    return List.of(
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

  private void login(String roleCode, MemberType memberType, Long memberId) {
    LoginUserContext.set(
      new LoginSession(
        "authorization-matrix",
        memberId,
        role(roleCode),
        memberType,
        roleCode,
        "matrix@example.test",
        "权限矩阵用户"
      )
    );
  }

  private Long role(String code) {
    return jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
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
