package com.yunfeil161.ailibrary.config.security.permission.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Permission {
    private  Integer id;
    private  String code;
    private String name;
    private String module;
    private String description;
    private Long version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
