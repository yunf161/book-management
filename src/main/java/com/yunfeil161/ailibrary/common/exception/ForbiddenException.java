package com.yunfeil161.ailibrary.common.exception;

/**
 * 已完成认证但没有操作权限时抛出，对应 HTTP 403。
 */
public class ForbiddenException extends RuntimeException {

    /**
     * 创建权限不足异常。
     *
     * @param message 返回给客户端的权限说明
     */
    public ForbiddenException(String message) {
        super(message);
    }
}
