package com.yunf161.bookmgmt.exception;

/**
 * 业务规则异常：用于表示领域规则不满足的场景。
 */
public class BusinessRuleException extends RuntimeException {
    /** 使用消息构造业务异常。 */
    public BusinessRuleException(String message) {
        super(message);
    }
}
