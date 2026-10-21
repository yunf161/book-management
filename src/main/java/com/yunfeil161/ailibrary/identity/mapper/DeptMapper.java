package com.yunfeil161.ailibrary.identity.mapper;


import com.yunfeil161.ailibrary.identity.entity.Dept;
import com.yunfeil161.ailibrary.identity.dto.DepartmentUpdateRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeptMapper {

    List<Dept> getDepartments(@Param("id") Long id, @Param("name") String name);

    Dept getDepartmentById(@Param("id") Long id);

    int updateDepartment(@Param("id") Long id, @Param("request") DepartmentUpdateRequest request);

    /**
     * 以当前读方式锁定并查询部门，供乐观锁更新失败后判断记录是否已删除。
     *
     * @param id 部门主键
     * @return 最新部门信息；记录不存在时为 null
     */
    Dept getDepartmentByIdForUpdate(@Param("id") Long id);

    /**
     * 新增部门。
     *
     * @param dept 要保存的部门信息
     * @return 受影响的行数
     */
    int addDepartment(@Param("dept") Dept dept);

    /**
     * 判断部门编码或名称是否已经被其他部门使用。
     *
     * @param code 部门编码
     * @param name 部门名称
     * @return 重复记录数
     */
    int countByCodeOrName(@Param("code") String code, @Param("name") String name);

    /**
     * 查询仍引用该部门的员工档案数量。
     *
     * @param departmentId 部门主键
     * @return 关联员工档案数量
     */
    int countEmployeesByDepartmentId(@Param("departmentId") Long departmentId);

    /**
     * 按主键删除部门。
     *
     * @param id 部门主键
     * @return 受影响的行数
     */
    int deleteDepartment(@Param("id") Long id);
}
