package com.yunfeil161.ailibrary.config.security.permission;

import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统一读取当前登录会话，并根据角色权限关系完成操作权限和数据范围校验。
 */
@Component
@RequiredArgsConstructor
public class PermissionAccess {

  private final PermissionQueryService permissionQueryService;

  /** 校验当前角色是否显式拥有指定权限。 */
  public LoginSession require(String permissionCode) {
    LoginSession session = LoginUserContext.get();
    // 正常请求会由 JWT 拦截器写入上下文；这里仍做兜底，避免内部调用或
    // 拦截器配置遗漏时因空指针被错误转换成 HTTP 500。
    if (session == null || session.getRoleId() == null) {
      throw new UnauthorizedException("未登录或凭证失效");
    }
    // 即使旧 Redis 会话尚未到期，也不能重新启用已退役的 AI 成员身份。
    if (session.getMemberType() == com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN) {
      throw new ForbiddenException("AI服务身份已停用");
    }
    if (
      !permissionQueryService.hasPermission(session.getRoleId(), permissionCode)
    ) {
      throw new ForbiddenException("没有操作权限");
    }
    return session;
  }

  /** 校验权限以及业务所需的最低数据范围。 */
  public LoginSession require(String permissionCode, DataScope requiredScope) {
    LoginSession session = require(permissionCode);
    DataScope actualScope = permissionQueryService
      .findDataScope(session.getRoleId(), permissionCode)
      .orElseThrow(() -> new ForbiddenException("没有数据权限"));
    if (!allows(actualScope, requiredScope)) {
      throw new ForbiddenException("数据权限范围不足");
    }
    return session;
  }

  /** 返回已授权权限的数据范围；无权限时直接拒绝。 */
  public DataScope requireScope(String permissionCode) {
    LoginSession session = require(permissionCode);
    return permissionQueryService
      .findDataScope(session.getRoleId(), permissionCode)
      .orElseThrow(() -> new ForbiddenException("没有数据权限"));
  }

  private boolean allows(DataScope actual, DataScope required) {
    if (required == null || required == DataScope.NONE) return true;
    if (required == DataScope.SELF) return (
      actual == DataScope.SELF || actual == DataScope.ALL
    );
    if (required == DataScope.DEPARTMENT) {
      return actual == DataScope.DEPARTMENT || actual == DataScope.ALL;
    }
    return actual == DataScope.ALL;
  }
}
