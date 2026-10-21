package com.yunfeil161.ailibrary.config.security.role.mapper;

import com.yunfeil161.ailibrary.config.security.role.entity.RolePermission;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionGrant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 角色权限关联数据访问接口。 */
@Mapper
public interface RolePermissionMapper {
    /** 查询启用角色的授权摘要；下划线字段由现有驼峰映射转换。 */
    List<PermissionGrant> findGrantedPermissions(@Param("roleId") Long roleId);
    RolePermission getByRoleIdAndPermissionCode(@Param("roleId") Long roleId,
                                                @Param("permissionCode") String permissionCode);

    int countByPermissionId(@Param("permissionId") Long permissionId);
}
