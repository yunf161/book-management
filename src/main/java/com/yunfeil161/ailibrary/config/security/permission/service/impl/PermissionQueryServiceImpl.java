package com.yunfeil161.ailibrary.config.security.permission.service.impl;

import com.yunfeil161.ailibrary.config.security.permission.service.PermissionQueryService;
import com.yunfeil161.ailibrary.config.security.role.entity.RolePermission;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import com.yunfeil161.ailibrary.config.security.role.mapper.RolePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** 权限判断服务实现。 */
@Service
@RequiredArgsConstructor
public class PermissionQueryServiceImpl implements PermissionQueryService {
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public boolean hasPermission(Long roleId, String permissionCode) {
        return findDataScope(roleId, permissionCode).isPresent();
    }

    @Override
    public Optional<DataScope> findDataScope(Long roleId, String permissionCode) {
        if (roleId == null || permissionCode == null || permissionCode.isBlank()) {
            return Optional.empty();
        }
        RolePermission relation = rolePermissionMapper.getByRoleIdAndPermissionCode(
                roleId, permissionCode);
        return relation == null ? Optional.empty() : Optional.of(relation.getDataScope());
    }
}
