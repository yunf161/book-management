package com.yunfeil161.ailibrary.config.security.permission.dto;

import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.Data;

/** 当前角色的一项显式授权；供界面展示使用，业务请求仍由服务器重新鉴权。 */
@Data
public class PermissionGrant {

  private String code;
  private DataScope dataScope;
}
