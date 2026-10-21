package com.yunfeil161.ailibrary.common.exception;

/**
 * 未登录或登录凭证失效时抛出的认证异常，对应 HTTP 401。
 */
public class UnauthorizedException extends RuntimeException {

    /**
     * 创建认证异常。
     *
     * @param message 返回给客户端的认证提示
     */
    public UnauthorizedException(String message) {
        super(message);
    }

    /**
     * 创建带原始原因的认证异常。
     *
     * @param message 返回给客户端的认证提示
     * @param cause 原始认证异常
     */
    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
