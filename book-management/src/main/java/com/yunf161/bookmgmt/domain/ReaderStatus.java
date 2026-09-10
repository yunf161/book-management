package com.yunf161.bookmgmt.domain;

/**
 * 读者状态枚举。
 */
public enum ReaderStatus {
    /** 正常可借。 */
    ACTIVE("ACTIVE", "正常"),
    /** 冻结不可借。 */
    FROZEN("FROZEN", "冻结"),
    /** 注销状态。 */
    DELETED("DELETED", "注销");

    /** 状态编码。 */
    private final String code;
    /** 状态描述。 */
    private final String description;

    /** 构造状态项。 */
    ReaderStatus(String code, String description) {
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

    /** 通过编码反查枚举。 */
    public static ReaderStatus fromCode(String code) {
        for (ReaderStatus status : ReaderStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }

}

