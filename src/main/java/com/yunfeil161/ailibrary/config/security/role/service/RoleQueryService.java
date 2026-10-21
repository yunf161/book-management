package com.yunfeil161.ailibrary.config.security.role.service;

import com.yunfeil161.ailibrary.config.security.role.entity.Role;

/** 供内部业务使用的角色查询服务，不提供 HTTP 接口。 */
public interface RoleQueryService {
    Role getRoleById(Long id);
    Role getRoleByCode(String code);
    Role requireActiveRole(Long id);
}
