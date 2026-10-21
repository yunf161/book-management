package com.yunfeil161.ailibrary.common.enums;

/**
 * 借阅资格状态，对应 member.qualification_status。
 * */
public enum QualificationStatus {
    /** 人类账号尚未申请资格。 */
    NOT_APPLIED,
    /** 资格申请正在审核。 */
    PENDING,
    /** 已通过资格审核。 */
    APPROVED,
    /** 资格申请被拒绝。 */
    REJECTED,
    /** 已暂停借阅资格。 */
    SUSPENDED,
    /** AI 馆员不适用借阅资格。 */
    NOT_APPLICABLE
}
