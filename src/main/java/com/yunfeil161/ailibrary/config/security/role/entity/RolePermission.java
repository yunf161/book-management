package com.yunfeil161.ailibrary.config.security.role.entity;

import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色与权限的关联及其数据范围，对应 role_permission 表。 */
@Data
public class RolePermission {
    private Long id;
    private Long roleId;
    private Long permissionId;
    private DataScope dataScope;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
