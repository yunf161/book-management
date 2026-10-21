package com.yunfeil161.ailibrary.config.security.role.service.impl;

import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.UnprocessableEntityException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.config.security.role.entity.Role;
import com.yunfeil161.ailibrary.config.security.role.enums.RoleStatus;
import com.yunfeil161.ailibrary.config.security.role.mapper.RoleMapper;
import com.yunfeil161.ailibrary.config.security.role.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 角色查询服务实现。 */
@Service
@RequiredArgsConstructor
public class RoleQueryServiceImpl implements RoleQueryService {
    private final RoleMapper roleMapper;

    @Override
    public Role getRoleById(Long id) {
        if (id == null) throw new WrongFormatException("角色ID不能为空");
        Role role = roleMapper.getRoleById(id);
        if (role == null) throw new NotFoundException("未找到角色信息");
        return role;
    }

    @Override
    public Role getRoleByCode(String code) {
        if (code == null || code.isBlank()) throw new WrongFormatException("角色编码不能为空");
        Role role = roleMapper.getRoleByCode(code);
        if (role == null) throw new NotFoundException("未找到角色信息");
        return role;
    }

    @Override
    public Role requireActiveRole(Long id) {
        Role role = getRoleById(id);
        if (role.getStatus() != RoleStatus.ACTIVE) {
            throw new UnprocessableEntityException("角色已停用，不能分配给成员");
        }
        return role;
    }
}
