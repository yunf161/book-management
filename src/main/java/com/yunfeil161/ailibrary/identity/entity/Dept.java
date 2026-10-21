package com.yunfeil161.ailibrary.identity.entity;

import com.yunfeil161.ailibrary.common.enums.DepartmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Dept {
    private Integer id;
    private String code;
    private String name;
    /** 部门启用状态，对应 department.status。 */
    private DepartmentStatus status;
    private String description;
    private Integer version;
    private java.time.LocalDateTime createdAt;
    private java.time.LocalDateTime updatedAt;

}
