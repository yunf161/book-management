package com.yunfeil161.ailibrary.common.enums;

/**
 * 成员领域类型。
 *
 * <p>该字段只决定成员使用哪一组扩展资料，不参与操作权限判断。管理员与普通馆员
 * 都属于 EMPLOYEE，具体权限完全由 member.role_id 关联的角色授权决定。</p>
 */
public enum MemberType {
    READER,
    EMPLOYEE,
    /** 仅兼容历史 JWT、审计和迁移数据；禁止新建、登录及业务访问。 */
    @Deprecated
    AI_LIBRARIAN
}
