package com.yunf161.bookmgmt.exception;

/**
 * 资源不存在异常：用于查询不到目标对象时抛出。
 */
public class NotFoundException extends RuntimeException {
    /** 使用消息构造不存在异常。 */
    public NotFoundException(String message) {
        super(message);
    }
}
