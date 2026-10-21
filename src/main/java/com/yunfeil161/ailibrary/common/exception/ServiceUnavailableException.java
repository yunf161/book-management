package com.yunfeil161.ailibrary.common.exception;

/**
 * 同步依赖服务暂时不可用时抛出，对应 HTTP 503。
 * 例如：模型、邮件、对象存储或消息服务不可用。
 */
public class ServiceUnavailableException extends RuntimeException {

    /**
     * 创建依赖不可用异常。
     *
     * @param message 返回给客户端的服务说明
     */
    public ServiceUnavailableException(String message) {
        super(message);
    }

    /**
     * 创建带原始原因的依赖不可用异常，便于日志记录调用失败原因。
     *
     * @param message 返回给客户端的服务说明
     * @param cause 原始调用异常
     */
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
