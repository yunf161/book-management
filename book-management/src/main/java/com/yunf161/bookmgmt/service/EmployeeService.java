package com.yunf161.bookmgmt.service;

import com.yunf161.bookmgmt.domain.Librarian;
import com.yunf161.bookmgmt.domain.LibrarianStatus;
import com.yunf161.bookmgmt.exception.BusinessRuleException;
import com.yunf161.bookmgmt.exception.NotFoundException;
import com.yunf161.bookmgmt.repository.EmployeeRepository;

import java.util.UUID;

/**
 * 员工服务：负责馆员创建、状态变更和基础资料维护。
 */
public class EmployeeService {
    /** 员工仓储依赖。 */
    private final EmployeeRepository employeeRepository;

    /** 构造员工服务。 */
    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /** 创建员工并初始化为在职状态。 */
    public Librarian createEmployee(String name, String phoneNumber, String role) {
        validateName(name);
        validatePhoneNumber(phoneNumber);
        validateRole(role);
        if (employeeRepository.findByPhoneNumber(phoneNumber).isPresent()) {
            throw new BusinessRuleException("[Employee][phone=" + phoneNumber + "] already registered");
        }
        Librarian employee = new Librarian(UUID.randomUUID().toString(), LibrarianStatus.ACTIVE, role);
        employee.setName(name);
        employee.setPhone_number(phoneNumber);
        employeeRepository.save(employee);
        return employee;
    }

    /** 按工号查询员工。 */
    public Librarian getEmployeeById(String employeeCode) {
        return employeeRepository.findById(employeeCode)
                .orElseThrow(() -> new NotFoundException("[Employee][code=" + employeeCode + "] not found"));
    }

    /** 按手机号查询员工。 */
    public Librarian getEmployeeByPhoneNumber(String phoneNumber) {
        return employeeRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new NotFoundException("[Employee][phone=" + phoneNumber + "] not found"));
    }

    /** 停用员工。 */
    public void disableEmployee(String employeeCode) {
        Librarian employee = getEmployeeById(employeeCode);
        if (employee.getStatus() == LibrarianStatus.DISABLED) {
            throw new BusinessRuleException("[Employee][code=" + employeeCode + "] already disabled");
        }
        employee.setStatus(LibrarianStatus.DISABLED);
        employeeRepository.save(employee);
    }

    /** 启用员工。 */
    public void activateEmployee(String employeeCode) {
        Librarian employee = getEmployeeById(employeeCode);
        if (employee.getStatus() == LibrarianStatus.ACTIVE) {
            throw new BusinessRuleException("[Employee][code=" + employeeCode + "] already active");
        }
        employee.setStatus(LibrarianStatus.ACTIVE);
        employeeRepository.save(employee);
    }

    /** 更新员工手机号。 */
    public void updateEmployeePhoneNumber(String employeeCode, String phoneNumber) {
        Librarian employee = getEmployeeById(employeeCode);
        validatePhoneNumber(phoneNumber);
        Librarian occupiedEmployee = employeeRepository.findByPhoneNumber(phoneNumber).orElse(null);
        if (occupiedEmployee != null && !occupiedEmployee.getEmployeeCode().equals(employeeCode)) {
            throw new BusinessRuleException("[Employee][phone=" + phoneNumber + "] already registered");
        }
        employee.setPhone_number(phoneNumber);
        employeeRepository.save(employee);
    }

    /** 更新员工姓名。 */
    public void updateEmployeeName(String employeeCode, String name) {
        Librarian employee = getEmployeeById(employeeCode);
        validateName(name);
        employee.setName(name);
        employeeRepository.save(employee);
    }

    /** 更新员工年龄。 */
    public void updateEmployeeAge(String employeeCode, int age) {
        Librarian employee = getEmployeeById(employeeCode);
        validateAge(age);
        employee.setAge(age);
        employeeRepository.save(employee);
    }

    /** 更新员工角色。 */
    public void updateEmployeeRole(String employeeCode, String role) {
        Librarian employee = getEmployeeById(employeeCode);
        validateRole(role);
        employee.setRole(role);
        employeeRepository.save(employee);
    }

    /** 校验姓名。 */
    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("[Employee] name cannot be null or blank");
        }
    }

    /** 校验手机号。 */
    private void validatePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new BusinessRuleException("[Employee] phone cannot be null or blank");
        }
    }

    /** 校验角色。 */
    private void validateRole(String role) {
        if (role == null || role.isBlank()) {
            throw new BusinessRuleException("[Employee] role cannot be null or blank");
        }
    }

    /** 校验年龄。 */
    private void validateAge(int age) {
        if (age < 0) {
            throw new BusinessRuleException("[Employee][age=" + age + "] cannot be negative");
        }
    }
}
