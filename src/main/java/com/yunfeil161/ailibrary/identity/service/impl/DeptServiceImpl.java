package com.yunfeil161.ailibrary.identity.service.impl;

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
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import com.yunfeil161.ailibrary.identity.dto.DepartmentUpdateRequest;
import com.yunfeil161.ailibrary.identity.entity.Dept;
import com.yunfeil161.ailibrary.identity.mapper.DeptMapper;
import com.yunfeil161.ailibrary.identity.service.DeptService;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

  private final DeptMapper deptMapper;
  private final CacheClient cacheClient;
  private static final String DEPT_CACHE_KEY_PREFIX = "dept:";

  @Override
  @DistributedLock(
    keys = "'lock:resource:department:' + (#id != null ? #id : 'query')",
    mode = LockMode.READ
  )
  public PageResult<Dept> getDepartments(
    Long id,
    String name,
    Integer page,
    Integer pageSize
  ) {
    if (id != null) {
      Dept department = cacheClient.queryWithPassThrough(
        DEPT_CACHE_KEY_PREFIX,
        id,
        Dept.class,
        deptMapper::getDepartmentById,
        10L,
        TimeUnit.MINUTES
      );
      if (department == null) {
        throw new NotFoundException("未找到部门信息");
      }
      return new PageResult<>(1L, List.of(department));
    }
    return getDepartmentsImpl(null, name, page, pageSize);
  }

  @Transactional
  @DistributedLock(keys = "'lock:resource:department:' + #id")
  @Override
  @Audited(resource = AuditResource.DEPARTMENT, id = "#id")
  public Dept updateDepartment(Long id, DepartmentUpdateRequest request) {
    if (id == null || request == null || request.getExpectedVersion() == null) {
      throw new WrongFormatException("部门ID、部门信息和期望版本号不能为空");
    }
    if (request.getExpectedVersion() < 0) {
      throw new WrongFormatException("期望版本号不能小于0");
    }
    if (request.getName() != null && isBlank(request.getName())) {
      throw new WrongFormatException("部门名称不能为空白字符串");
    }
    if (deptMapper.getDepartmentById(id) == null) {
      throw new NotFoundException("未找到部门信息");
    }

    int affectedRows = deptMapper.updateDepartment(id, request);
    if (affectedRows == 0) {
      if (deptMapper.getDepartmentByIdForUpdate(id) == null) {
        throw new NotFoundException("部门已被删除");
      }
      throw new ConflictException("部门信息已被其他用户修改，请刷新后重试");
    }
    if (affectedRows > 1) {
      throw new IllegalStateException("部门更新影响了多条记录");
    }
    String key = DEPT_CACHE_KEY_PREFIX + id;
    cacheClient.deleteRequired(key);
    return deptMapper.getDepartmentById(id);
  }

  /** 新增部门，并确保部门编码和名称不重复。 */
  @Transactional
  @DistributedLock(keys = "'lock:resource:department:create'")
  @Override
  @Audited(resource = AuditResource.DEPARTMENT, id = "")
  public String addDepartment(Dept dept) {
    if (dept == null || isBlank(dept.getCode()) || isBlank(dept.getName())) {
      throw new IllegalArgumentException("部门编码和名称不能为空");
    }
    if (deptMapper.countByCodeOrName(dept.getCode(), dept.getName()) > 0) {
      throw new ConflictException("部门编码或名称已存在");
    }
    if (deptMapper.addDepartment(dept) != 1) {
      throw new IllegalStateException("新增部门失败");
    }
    cacheClient.deleteRequired(DEPT_CACHE_KEY_PREFIX + dept.getId());
    return dept.getName();
  }

  /** 删除部门前检查是否仍有关联员工。 */
  @Transactional
  @DistributedLock(keys = "'lock:resource:department:' + #id")
  @Override
  @Audited(resource = AuditResource.DEPARTMENT, id = "#id")
  public String deleteDepartment(Long id) {
    if (id == null) {
      throw new WrongFormatException("部门ID不能为空");
    }
    Dept existingDept = requireDepartment(id);
    if (deptMapper.countEmployeesByDepartmentId(id) > 0) {
      throw new ConflictException("部门仍有关联员工，不能删除");
    }
    if (deptMapper.deleteDepartment(id) != 1) {
      throw new NotFoundException("部门已被删除");
    }

    String key = DEPT_CACHE_KEY_PREFIX + id;
    cacheClient.deleteRequired(key);
    return existingDept.getName();
  }

  private Dept requireDepartment(Long id) {
    Dept department = deptMapper.getDepartmentById(id);
    if (department == null) {
      throw new NotFoundException("未找到部门信息");
    }
    return department;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private void startPage(Integer page, Integer pageSize) {
    int normalizedPage = page == null || page < 1 ? 1 : page;
    int normalizedSize =
      pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    PageHelper.startPage(normalizedPage, normalizedSize);
  }

  public PageResult<Dept> getDepartmentsImpl(
    Long id,
    String name,
    Integer page,
    Integer pageSize
  ) {
    startPage(page, pageSize);

    List<Dept> departments = deptMapper.getDepartments(id, name);
    if (departments == null || departments.isEmpty()) {
      throw new NotFoundException("未找到部门信息");
    }
    PageInfo<Dept> pageInfo = new PageInfo<>(departments);
    return new PageResult<>(pageInfo.getTotal(), pageInfo.getList());
  }
}
