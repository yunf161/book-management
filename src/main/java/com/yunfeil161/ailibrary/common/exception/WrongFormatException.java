package com.yunfeil161.ailibrary.common.exception;

/**
 * 请求格式或字段值不符合接口要求时抛出，对应 HTTP 400。
 * 例如：邮箱格式错误、缺少必填字段或参数类型不正确。
 */
public class WrongFormatException extends RuntimeException {

    /**
     * 创建请求格式异常。
     *
     * @param message 返回给客户端的字段错误提示
     */
    public WrongFormatException(String message) {
        super(message);
    }
}
