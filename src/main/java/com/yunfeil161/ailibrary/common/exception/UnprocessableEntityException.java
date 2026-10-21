package com.yunfeil161.ailibrary.common.exception;

/**
 * 请求格式正确但不满足业务条件时抛出，对应 HTTP 422。
 * 例如：借阅资格未通过、借阅额度不足或预约数量达到上限。
 */
public class UnprocessableEntityException extends RuntimeException {

    /**
     * 创建业务条件不满足异常。
     *
     * @param message 返回给客户端的业务提示
     */
    public UnprocessableEntityException(String message) {
        super(message);
    }
}
