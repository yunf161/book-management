package com.yunf161.bookmgmt.domain;

/**
 * 图书副本状态枚举。
 */
public enum BookCopyStatus {
    /** 可借阅。 */
    AVAILABLE("AVAILABLE", "可借"),
    /** 已借出。 */
    BORROWED("BORROWED", "已借出"),
    /** 丢失。 */
    LOST("LOST", "丢失"),
    /** 下架。 */
    OFF_SHELF("OFF_SHELF", "下架");

    /** 状态编码。 */
    private final String code;
    /** 状态描述。 */
    private final String description;

    /** 构造状态项。 */
    BookCopyStatus(String code, String description) {
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
