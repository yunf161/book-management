package com.yunfeil161.ailibrary.config.security.permission.controller;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionFindParam;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionUpdateRequest;
import com.yunfeil161.ailibrary.config.security.permission.entity.Permission;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionService;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import com.yunfeil161.ailibrary.config.security.role.enums.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端权限查询与维护接口。 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/permissions")
public class PermissionController {
    private final PermissionService permissionService;
    private final PermissionAccess permissionAccess;

    /** 按条件分页查询权限。 */
    @GetMapping
    public Result getPermissions(@ModelAttribute PageQuery pageQuery,
                                 @ModelAttribute PermissionFindParam findParam) {
        permissionAccess.require(PermissionCodes.PERMISSION_QUERY, DataScope.ALL);
        return Result.success(permissionService.getPermissionsByPages(
                findParam, pageQuery.getPage(), pageQuery.getPageSize()));
    }

    /** 查询单条权限详情。 */
    @GetMapping("/{id}")
    public Result getPermissionById(@PathVariable Long id) {
        permissionAccess.require(PermissionCodes.PERMISSION_QUERY, DataScope.ALL);
        return Result.success(permissionService.getPermissionById(id));
    }

    /** 创建权限；code 是固定权限编码，创建后不再修改。 */
    @PostMapping
    public Result addPermission(@RequestBody Permission permission) {
        permissionAccess.require(PermissionCodes.PERMISSION_CREATE, DataScope.ALL);
        return Result.success(permissionService.addPermission(permission));
    }

    /** 修改权限的名称、模块或描述；权限授权和数据范围由角色关联维护。 */
    @PutMapping("/{id}")
    public Result updatePermission(@PathVariable Long id, @RequestBody PermissionUpdateRequest request) {
        permissionAccess.require(PermissionCodes.PERMISSION_UPDATE, DataScope.ALL);
        return Result.success(permissionService.updatePermission(id, request));
    }

    /** 删除权限。 */
    @DeleteMapping("/{id}")
    public Result deletePermission(@PathVariable Long id) {
        permissionAccess.require(PermissionCodes.PERMISSION_DELETE, DataScope.ALL);
        return Result.success(permissionService.deletePermission(id));
    }
}
