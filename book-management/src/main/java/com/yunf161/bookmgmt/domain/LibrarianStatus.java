package com.yunf161.bookmgmt.domain;

/**
 * 馆员状态枚举。
 */
public enum LibrarianStatus {
    /** 在职可用。 */
    ACTIVE("ACTIVE", "在职"),
    /** 停用。 */
    DISABLED("DISABLED", "停用");

    /** 状态编码。 */
    private final String code;
    /** 状态描述。 */
    private final String description;

    /** 构造状态项。 */
    LibrarianStatus(String code, String description) {
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
