package com.yunfeil161.ailibrary.config.security.permission;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.config.security.permission.service.CurrentPermissionService;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 验证本人授权摘要、数据范围映射和动态撤销；真实JWT另由HTTP验收覆盖。 */
@SpringBootTest(
  properties = "spring.datasource.url=jdbc:h2:mem:current_permissions;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class CurrentPermissionsIntegrationTests {

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  @Autowired
  CurrentPermissionService permissions;

  @AfterEach
  void clearContext() {
    LoginUserContext.clear();
  }

  private Long identity(String code) {
    Long role = jdbc.queryForObject(
      "select id from `role` where code=?",
      Long.class,
      code
    );
    LoginSession session = new LoginSession();
    session.setMemberId(123L);
    session.setRoleId(role);
    LoginUserContext.set(session);
    return role;
  }

  @Test
  void anonymousIsRejectedEvenWithoutInterceptor() throws Exception {
    mvc.perform(get("/me/permissions")).andExpect(status().isUnauthorized());
  }

  @Test
  void suppliedRoleOrMemberCannotSelectAnotherIdentity() throws Exception {
    Long role = identity("READER");
    mvc
      .perform(
        get("/me/permissions").param("roleId", "999").param("memberId", "999")
      )
      .andExpect(status().isOk())
      .andExpect(header().string("Cache-Control", "no-store"))
      .andExpect(jsonPath("$.data.memberId").value(123))
      .andExpect(jsonPath("$.data.roleId").value(role));
    assertFalse(
      permissions
        .current()
        .permissions()
        .stream()
        .anyMatch(p -> p.getCode().equals("permission:create"))
    );
  }

  @Test
  void selfScopeUsesCamelCaseMapping() {
    identity("READER");
    var grant = permissions
      .current()
      .permissions()
      .stream()
      .filter(p -> p.getCode().equals("member:query"))
      .findFirst()
      .orElseThrow();
    assertEquals(DataScope.SELF, grant.getDataScope());
  }

  @Test
  void revokedGrantDisappearsOnNextQuery() {
    Long role = identity("ADMIN");
    assertTrue(
      permissions
        .current()
        .permissions()
        .stream()
        .anyMatch(p -> p.getCode().equals("permission:create"))
    );
    jdbc.update(
      "delete from role_permission where role_id=? and permission_id=(select id from permission where code='permission:create')",
      role
    );
    assertFalse(
      permissions
        .current()
        .permissions()
        .stream()
        .anyMatch(p -> p.getCode().equals("permission:create"))
    );
  }

  @Test
  void disabledRoleReturnsNoGrants() {
    Long role = identity("ADMIN");
    jdbc.update("update `role` set status='DISABLED' where id=?", role);
    assertTrue(permissions.current().permissions().isEmpty());
  }

  @Test
  void unknownRoleHasNoGrants() {
    identity("READER");
    LoginUserContext.get().setRoleId(Long.MAX_VALUE);
    assertTrue(permissions.current().permissions().isEmpty());
  }
}
