package com.yunfeil161.ailibrary.config.security.permission.service;

import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionFindParam;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionUpdateRequest;
import com.yunfeil161.ailibrary.config.security.permission.entity.Permission;

public interface PermissionService {
    PageResult<Permission> getPermissionsByPages(PermissionFindParam param, Integer page, Integer size);
    Permission getPermissionById(Long id);
    String addPermission(Permission permission);
    Permission updatePermission(Long id, PermissionUpdateRequest request);
    String deletePermission(Long id);
}
