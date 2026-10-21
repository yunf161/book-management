package com.yunfeil161.ailibrary.config.security.permission;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 验证统一权限入口在拦截器之外被调用时也能返回正确的认证错误。 */
class PermissionAccessTests {

  @AfterEach
  void clearContext() {
    LoginUserContext.clear();
  }

  @Test
  void missingSessionReturnsUnauthorizedInsteadOfNullPointer() {
    PermissionAccess access = new PermissionAccess(
      mock(PermissionQueryService.class)
    );

    assertThrows(
      UnauthorizedException.class,
      () -> access.require(PermissionCodes.MEMBER_QUERY)
    );
  }
}
