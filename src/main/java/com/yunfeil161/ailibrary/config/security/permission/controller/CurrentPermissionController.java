package com.yunfeil161.ailibrary.config.security.permission.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.config.security.permission.service.CurrentPermissionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录即可查询本人的授权摘要，无需管理员权限，也不授予任何新的业务权限。 */
@RestController
@RequiredArgsConstructor
public class CurrentPermissionController {

  private final CurrentPermissionService permissions;

  @GetMapping("/me/permissions")
  public Result current(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
    return Result.success(permissions.current());
  }
}
