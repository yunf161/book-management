package com.yunfeil161.ailibrary.config.security.permission.service;

import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;
import com.yunfeil161.ailibrary.config.security.permission.dto.CurrentPermissionsResponse;
import com.yunfeil161.ailibrary.config.security.role.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 每次从数据库读取当前角色的启用授权，避免缓存让撤销权限继续出现在界面。 */
@Service
@RequiredArgsConstructor
public class CurrentPermissionService {

  private final RolePermissionMapper roles;

  public CurrentPermissionsResponse current() {
    var session = LoginUserContext.get();
    if (
      session == null ||
      session.getMemberId() == null ||
      session.getRoleId() == null
    ) {
      throw new UnauthorizedException("未登录或凭证失效");
    }
    return new CurrentPermissionsResponse(
      session.getMemberId(),
      session.getRoleId(),
      roles.findGrantedPermissions(session.getRoleId())
    );
  }
}
