package com.yunfeil161.ailibrary.identity.dto;

import com.yunfeil161.ailibrary.common.api.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 部门统一分页查询参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepartmentQueryRequest extends PageQuery {
    /** 部门主键，精确查询。 */
    private Long id;

    /** 部门名称，模糊查询。 */
    private String name;
}
