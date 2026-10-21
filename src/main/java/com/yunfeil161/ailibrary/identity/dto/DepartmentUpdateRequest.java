package com.yunfeil161.ailibrary.identity.dto;

import com.yunfeil161.ailibrary.common.enums.DepartmentStatus;
import lombok.Data;

/**
 * 部门更新请求。
 * 部门编码是创建后的稳定业务标识，因此不提供 code 字段。
 */
@Data
public class DepartmentUpdateRequest {

    /** 部门名称。 */
    private String name;

    /** 部门职责说明；传入 null 时清空说明。 */
    private String description;

    /** 标识请求 JSON 是否显式包含 description，区分未修改与清空。 */
    private boolean descriptionProvided;

    /**
     * Jackson 在请求中出现 description 时调用此方法，即使值为 null。
     *
     * @param description 新说明；null 表示清空
     */
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    /** 部门启用状态。 */
    private DepartmentStatus status;

    /** 客户端读取部门详情时取得的版本号。 */
    private Integer expectedVersion;
}
