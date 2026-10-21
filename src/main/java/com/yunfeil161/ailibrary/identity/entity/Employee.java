package com.yunfeil161.ailibrary.identity.entity;

import com.yunfeil161.ailibrary.common.enums.EmploymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** 员工领域对象，由 member 和 employee_profile 组装。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class Employee extends Member {
    private Long employeeProfileId;
    private String employeeNo;
    private Long departmentId;
    private String positionName;
    private EmploymentStatus employmentStatus;
    private LocalDate hiredOn;
    private LocalDate leftOn;
}
