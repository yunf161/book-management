package com.yunfeil161.ailibrary.common.exception;

/**
 * 请求的资源不存在或对当前用户不可见时抛出，对应 HTTP 404。
 */
public class NotFoundException extends RuntimeException {

    /**
     * 创建资源不存在异常。
     *
     * @param message 返回给客户端的资源说明
     */
    public NotFoundException(String message) {
        super(message);
    }
}
