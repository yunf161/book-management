package com.yunfeil161.ailibrary.identity.service;

import com.yunfeil161.ailibrary.common.api.PageResult;
import com.yunfeil161.ailibrary.identity.entity.Employee;
import com.yunfeil161.ailibrary.identity.entity.Reader;
import java.util.List;

public interface MemberService {
  PageResult<Reader> getReaders(Reader reader, Integer page, Integer pageSize);
  PageResult<Employee> getEmployees(
    Employee employee,
    Integer page,
    Integer pageSize
  );

  Reader addReader(Reader reader, List<String> interests);
  Employee addEmployee(Employee employee);

  Reader updateReader(
    Long id,
    Reader reader,
    List<String> interests,
    Long expectedVersion
  );
  Employee updateEmployee(Long id, Employee employee, Long expectedVersion);

  /** 认证模块完成验证码校验后，使用已经哈希的密码更新成员。 */
  void updatePassword(Long id, String passwordHash);

  String deleteReader(Long id);
  String deleteEmployee(Long id);
}
