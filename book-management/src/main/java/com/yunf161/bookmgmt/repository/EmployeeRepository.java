package com.yunf161.bookmgmt.repository;

import com.yunf161.bookmgmt.domain.Librarian;

import java.util.Optional;

/**
 * 员工（馆员）仓储接口。
 */
public interface EmployeeRepository {
    /** 保存员工。 */
    void save(Librarian employee);

    /** 按工号查询员工。 */
    Optional<Librarian> findById(String employeeCode);

    /** 按手机号查询员工。 */
    Optional<Librarian> findByPhoneNumber(String phoneNumber);

    /** 按工号删除员工。 */
    void deleteById(String employeeCode);
}
