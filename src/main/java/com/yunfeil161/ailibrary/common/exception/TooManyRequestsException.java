package com.yunfeil161.ailibrary.common.exception;

/**
 * 请求超过限流规则时抛出，对应 HTTP 429。
 * 例如：频繁发送验证码或连续提交受限操作。
 */
public class TooManyRequestsException extends RuntimeException {

    /**
     * 创建请求过于频繁异常。
     *
     * @param message 返回给客户端的限流提示
     */
    public TooManyRequestsException(String message) {
        super(message);
    }
}
