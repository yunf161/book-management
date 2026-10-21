package com.yunfeil161.ailibrary.config.security.permission;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 验证权限查询同时尊重角色的启用状态。 */
@SpringBootTest(
  properties =
    "spring.datasource.url=jdbc:h2:mem:permission_query_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
)
class PermissionQueryServiceIntegrationTests {

  @Autowired
  private PermissionQueryService permissionQueryService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void disabledRoleLosesPermissionsImmediately() {
    Long readerRoleId = jdbcTemplate.queryForObject(
      "select id from `role` where code = 'READER'",
      Long.class
    );

    assertTrue(
      permissionQueryService.hasPermission(
        readerRoleId,
        PermissionCodes.MEMBER_QUERY
      )
    );

    try {
      jdbcTemplate.update(
        "update `role` set status = 'DISABLED' where id = ?",
        readerRoleId
      );

      assertFalse(
        permissionQueryService.hasPermission(
          readerRoleId,
          PermissionCodes.MEMBER_QUERY
        )
      );
    } finally {
      // 测试使用共享H2数据库，必须恢复默认角色，避免影响其他集成测试。
      jdbcTemplate.update(
        "update `role` set status = 'ACTIVE' where id = ?",
        readerRoleId
      );
    }
  }
}
