package com.yunfeil161.ailibrary.identity.service;

import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.identity.dto.DepartmentUpdateRequest;
import com.yunfeil161.ailibrary.identity.entity.Dept;
import org.springframework.stereotype.Service;

@Service
public interface DeptService {
    PageResult<Dept> getDepartments(Long id, String name, Integer page, Integer pageSize);

    Dept updateDepartment(Long id, DepartmentUpdateRequest request);

    String addDepartment(Dept dept);

    String deleteDepartment(Long id);
}
