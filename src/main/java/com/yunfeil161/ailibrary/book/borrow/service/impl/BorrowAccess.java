package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 借阅模块内部权限检查器，统一验证角色权限和数据范围。 */
@Component
@RequiredArgsConstructor
class BorrowAccess {

  private final PermissionAccess permissionAccess;

  /**
   * 复用全局权限入口；借阅业务只需声明权限编码和所需数据范围。
   */
  LoginSession require(String code, DataScope minimum) {
    return permissionAccess.require(code, minimum);
  }
}
