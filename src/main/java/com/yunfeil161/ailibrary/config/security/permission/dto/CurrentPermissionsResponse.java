package com.yunfeil161.ailibrary.config.security.permission.dto;

import java.util.List;

/** 只返回当前登录成员的角色授权，不接受客户端指定成员或角色。 */
public record CurrentPermissionsResponse(
  Long memberId,
  Long roleId,
  List<PermissionGrant> permissions
) {}
