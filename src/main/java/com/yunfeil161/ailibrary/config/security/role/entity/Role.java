package com.yunfeil161.ailibrary.config.security.role.entity;

import com.yunfeil161.ailibrary.config.security.role.enums.RoleStatus;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统角色，对应 role 表。 */
@Data
public class Role {
    private Long id;
    private String code;
    private String name;
    private String description;
    private RoleStatus status;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
