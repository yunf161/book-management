package com.yunfeil161.ailibrary.config.security.permission.mapper;

import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionFindParam;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionUpdateRequest;
import com.yunfeil161.ailibrary.config.security.permission.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 权限表数据访问接口。 */
@Mapper
public interface PermissionMapper {
    List<Permission> getPermissionsByPages(@Param("param") PermissionFindParam param);
    Permission getPermissionById(@Param("id") Long id);
    Permission getPermissionByCode(@Param("code") String code);
    int countByCode(@Param("code") String code);
    int addPermission(@Param("permission") Permission permission);
    int updatePermission(@Param("id") Long id, @Param("request") PermissionUpdateRequest request);
    int deletePermission(@Param("id") Long id);
}
