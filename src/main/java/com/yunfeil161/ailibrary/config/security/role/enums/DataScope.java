package com.yunfeil161.ailibrary.config.security.role.enums;

/** 角色执行某项权限时允许访问的数据范围。 */
public enum DataScope {
    /** 该权限不涉及数据范围。 */
    NONE,
    /** 仅本人数据。 */
    SELF,
    /** 本部门数据。 */
    DEPARTMENT,
    /** 全部数据。 */
    ALL
}
