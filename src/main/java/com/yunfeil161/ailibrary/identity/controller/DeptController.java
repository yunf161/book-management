package com.yunfeil161.ailibrary.identity.controller;

import com.yunfeil161.ailibrary.common.api.Result;
import com.yunfeil161.ailibrary.identity.dto.DepartmentQueryRequest;
import com.yunfeil161.ailibrary.identity.dto.DepartmentUpdateRequest;
import com.yunfeil161.ailibrary.identity.entity.Dept;
import com.yunfeil161.ailibrary.identity.service.DeptService;
import com.yunfeil161.ailibrary.config.security.permission.PermissionAccess;
import com.yunfeil161.ailibrary.config.security.permission.PermissionCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DeptController {
    private final DeptService deptService;
    private final PermissionAccess permissionAccess;

    /**
     * 统一分页查询部门。id 为精确条件，name 为模糊条件，均不传时查询全部。
     */
    @GetMapping("/admin/departments")
    public Result getDepartments(@ModelAttribute DepartmentQueryRequest query) {
        permissionAccess.require(PermissionCodes.DEPARTMENT_QUERY);
        log.info("查询部门：id={}, name={}, page={}, pageSize={}",
                query.getId(), query.getName(), query.getPage(), query.getPageSize());
        return Result.success(
                deptService.getDepartments(
                        query.getId(),
                        query.getName(),
                        query.getPage(),
                        query.getPageSize()
                )
        );
    }

    @PostMapping("/admin/departments")
    public Result addDepartment(@RequestBody Dept dept) {
        permissionAccess.require(PermissionCodes.DEPARTMENT_CREATE);
        log.info("添加部门：dept={}", dept);
        String name = deptService.addDepartment(dept);
        return Result.success("成功添加部门," + name);
    }

    @PutMapping("/admin/departments/{id}")
    public Result updateDepartment(@PathVariable Long id,
                                   @RequestBody DepartmentUpdateRequest request) {
        permissionAccess.require(PermissionCodes.DEPARTMENT_UPDATE);
        log.info("更新部门：id={}, request={}", id, request);
        return Result.success(deptService.updateDepartment(id, request));
    }

    @DeleteMapping("/admin/departments/{id}")
    public Result deleteDepartment(@PathVariable Long id) {
        permissionAccess.require(PermissionCodes.DEPARTMENT_DELETE);
        log.info("删除部门：id={}", id);
        String name = deptService.deleteDepartment(id);
        return Result.success("成功删除部门," + name);
    }
}
