package com.yunfeil161.ailibrary.config.security.permission.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.yunfeil161.ailibrary.audit.AuditResource;
import com.yunfeil161.ailibrary.audit.Audited;
import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.common.exception.WrongFormatException;
import com.yunfeil161.ailibrary.common.redis.lock.BusinessLockKeys;
import com.yunfeil161.ailibrary.common.redis.lock.DistributedLock;
import com.yunfeil161.ailibrary.common.redis.lock.LockMode;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionFindParam;
import com.yunfeil161.ailibrary.config.security.permission.dto.PermissionUpdateRequest;
import com.yunfeil161.ailibrary.config.security.permission.entity.Permission;
import com.yunfeil161.ailibrary.config.security.permission.mapper.PermissionMapper;
import com.yunfeil161.ailibrary.config.security.permission.service.PermissionService;
import com.yunfeil161.ailibrary.config.security.role.mapper.RolePermissionMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 权限管理业务实现。 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

  private final PermissionMapper permissionMapper;
  private final RolePermissionMapper rolePermissionMapper;

  @Override
  @DistributedLock(
    keys = "'lock:resource:permission:query'",
    mode = LockMode.READ
  )
  public PageResult<Permission> getPermissionsByPages(
    PermissionFindParam param,
    Integer page,
    Integer size
  ) {
    // 与其他聚合查询保持一致，省略分页参数时使用默认值，避免拆箱空指针。
    PageHelper.startPage(
      page == null || page < 1 ? 1 : page,
      size == null || size < 1 ? 10 : Math.min(size, 100)
    );
    List<Permission> permissions = permissionMapper.getPermissionsByPages(
      param
    );
    if (permissions == null || permissions.isEmpty()) {
      throw new NotFoundException("未找到权限信息");
    }
    PageInfo<Permission> pageInfo = new PageInfo<>(permissions);
    return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
  }

  @Override
  @DistributedLock(
    keys = "'lock:resource:permission:' + #id",
    mode = LockMode.READ
  )
  public Permission getPermissionById(Long id) {
    if (id == null) throw new WrongFormatException("权限ID不能为空");
    Permission permission = permissionMapper.getPermissionById(id);
    if (permission == null) throw new NotFoundException("未找到权限信息");
    return permission;
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:permission:create'")
  @Audited(resource = AuditResource.PERMISSION, id = "")
  public String addPermission(Permission permission) {
    validate(permission, true);
    if (permissionMapper.countByCode(permission.getCode()) > 0) {
      throw new ConflictException("权限编码已存在");
    }
    if (permissionMapper.addPermission(permission) != 1) {
      throw new IllegalStateException("新增权限失败");
    }
    return permission.getCode();
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:permission:' + #id")
  @Audited(resource = AuditResource.PERMISSION, id = "#id")
  public Permission updatePermission(Long id, PermissionUpdateRequest request) {
    if (id == null || request == null || request.getExpectedVersion() == null) {
      throw new WrongFormatException("权限ID、权限信息和期望版本号不能为空");
    }
    if (request.getExpectedVersion() < 0) {
      throw new WrongFormatException("期望版本号不能小于 0");
    }
    if (permissionMapper.getPermissionById(id) == null) {
      throw new NotFoundException("未找到权限信息");
    }
    validate(request);
    int affectedRows = permissionMapper.updatePermission(id, request);
    if (affectedRows == 0) {
      if (permissionMapper.getPermissionById(id) == null) {
        throw new NotFoundException("权限已被删除");
      }
      throw new ConflictException("权限信息已被其他用户修改，请刷新后重试");
    }
    if (affectedRows > 1) {
      throw new IllegalStateException("权限更新影响了多条记录");
    }
    return getPermissionById(id);
  }

  @Override
  @Transactional
  @DistributedLock(keys = "'lock:resource:permission:' + #id")
  @Audited(resource = AuditResource.PERMISSION, id = "#id")
  public String deletePermission(Long id) {
    Permission permission = getPermissionById(id);
    if (rolePermissionMapper.countByPermissionId(id) > 0) {
      throw new ConflictException("权限仍被角色引用，不能删除");
    }
    if (permissionMapper.deletePermission(id) != 1) {
      throw new NotFoundException("权限已被删除");
    }
    return permission.getCode();
  }

  private void validate(Permission permission, boolean requireCode) {
    if (
      permission == null ||
      (requireCode && isBlank(permission.getCode())) ||
      isBlank(permission.getName()) ||
      isBlank(permission.getModule()) ||
      isBlank(permission.getDescription())
    ) {
      throw new WrongFormatException("权限编码、名称、模块和描述不能为空");
    }
  }

  private void validate(PermissionUpdateRequest request) {
    if (
      isBlank(request.getName()) ||
      isBlank(request.getModule()) ||
      isBlank(request.getDescription())
    ) {
      throw new WrongFormatException("权限名称、模块和描述不能为空");
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
