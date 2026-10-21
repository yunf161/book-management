package com.yunfeil161.ailibrary.config.security.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PermissionFindParam {
    private  Integer id;
    private  String code;
    private String name;
    private String module;
}
