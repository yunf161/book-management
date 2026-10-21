package com.yunfeil161.ailibrary.common.exception;

/**
 * 当前资源状态或版本与本次操作冲突时抛出，对应 HTTP 409。
 * 例如：工号重复、删除仍有员工的部门或乐观锁版本过期。
 */
public class ConflictException extends RuntimeException {

    /**
     * 创建状态冲突异常。
     *
     * @param message 返回给客户端的冲突说明
     */
    public ConflictException(String message) {
        super(message);
    }
}
