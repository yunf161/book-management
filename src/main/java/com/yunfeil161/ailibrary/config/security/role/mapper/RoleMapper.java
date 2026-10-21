package com.yunfeil161.ailibrary.config.security.role.mapper;

import com.yunfeil161.ailibrary.config.security.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 角色只读数据访问接口。 */
@Mapper
public interface RoleMapper {
    Role getRoleById(@Param("id") Long id);
    Role getRoleByCode(@Param("code") String code);
}
