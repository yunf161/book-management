package com.yunfeil161.ailibrary.config.security.permission.service;

import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;

import java.util.Optional;

/**
 * 供安全组件调用的内部权限判断服务，不提供 HTTP 接口。
 */
public interface PermissionQueryService {

    /** 根据角色与权限编码判断是否存在显式授权。 */
    boolean hasPermission(Long roleId, String permissionCode);

    /** 查询角色执行指定权限时的数据范围；未授权时返回空。 */
    Optional<DataScope> findDataScope(Long roleId, String permissionCode);
}
