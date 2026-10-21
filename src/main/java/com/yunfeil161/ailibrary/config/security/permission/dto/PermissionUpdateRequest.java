package com.yunfeil161.ailibrary.config.security.permission.dto;

import lombok.Data;

/** 权限更新请求，权限编码创建后保持不变。 */
@Data
public class PermissionUpdateRequest {
    private String name;
    private String module;
    private String description;
    private Long expectedVersion;
}
