package com.yunf161.bookmgmt.domain;

/**
 * 借阅记录状态枚举。
 */
public enum LoanStatus {
    /** 借阅中。 */
    BORROWING("BORROWING", "借阅中"),
    /** 已归还。 */
    RETURNED("RETURNED", "已归还"),
    /** 已逾期。 */
    OVERDUE("OVERDUE", "已逾期");

    /** 状态编码。 */
    private final String code;
    /** 状态描述。 */
    private final String description;

    /** 构造状态项。 */
    LoanStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /** 获取状态编码。 */
    public String getCode() {
        return code;
    }

    /** 获取状态描述。 */
    public String getDescription() {
        return description;
    }
}
