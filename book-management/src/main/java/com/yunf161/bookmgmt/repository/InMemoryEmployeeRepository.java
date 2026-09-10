package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Librarian;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 员工（馆员）仓储的内存实现。
 */
public class InMemoryEmployeeRepository implements EmployeeRepository {
    /** 主存储，按 employeeCode 保存员工。 */
    private final MainMemoryTemp<Librarian> baseRepository;
    /** 二级索引：phone -> employeeCode。 */
    private final Map<String, String> phoneToEmployeeCode;
    /** 反向索引：employeeCode -> phone，用于更新手机号时清理旧索引。 */
    private final Map<String, String> employeeCodeToPhone;

    /** 初始化员工内存仓储。 */
    public InMemoryEmployeeRepository() {
        this.baseRepository = new MainMemoryTemp<>(Librarian::getEmployeeCode);
        this.phoneToEmployeeCode = new ConcurrentHashMap<>();
        this.employeeCodeToPhone = new ConcurrentHashMap<>();
    }

    /** 保存员工并更新手机号索引。 */
    @Override
    public void save(Librarian employee) {
        if (employee.getEmployeeCode() == null || employee.getEmployeeCode().isBlank()) {
            throw new IllegalArgumentException("employeeCode cannot be null or blank");
        }
        if (employee.getPhone_number() == null || employee.getPhone_number().isBlank()) {
            throw new IllegalArgumentException("phoneNumber cannot be null or blank");
        }
        String oldPhone = employeeCodeToPhone.get(employee.getEmployeeCode());
        if (oldPhone != null && !oldPhone.equals(employee.getPhone_number())) {
            phoneToEmployeeCode.remove(oldPhone);
        }
        baseRepository.save(employee);
        phoneToEmployeeCode.put(employee.getPhone_number(), employee.getEmployeeCode());
        employeeCodeToPhone.put(employee.getEmployeeCode(), employee.getPhone_number());
    }

    /** 按工号查询员工。 */
    @Override
    public Optional<Librarian> findById(String employeeCode) {
        return baseRepository.findById(employeeCode);
    }

    /** 按手机号查询员工。 */
    @Override
    public Optional<Librarian> findByPhoneNumber(String phoneNumber) {
        String employeeCode = phoneToEmployeeCode.get(phoneNumber);
        if (employeeCode == null) {
            return Optional.empty();
        }
        return baseRepository.findById(employeeCode);
    }

    /** 删除员工并同步清理手机号索引。 */
    @Override
    public void deleteById(String employeeCode) {
        Optional<Librarian> existingEmployee = baseRepository.findById(employeeCode);
        if (existingEmployee.isPresent()) {
            String oldPhone = employeeCodeToPhone.remove(employeeCode);
            if (oldPhone != null) {
                phoneToEmployeeCode.remove(oldPhone);
            }
        }
        baseRepository.deleteById(employeeCode);
    }
}
