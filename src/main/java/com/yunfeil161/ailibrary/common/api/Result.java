package com.yunfeil161.ailibrary.common.api;

import lombok.Data;

/**
 * 所有 HTTP 接口统一返回的响应体。
 * code 为 1 表示成功；失败时与 HTTP 状态码保持一致；data 承载任意业务数据；msg 为提示文本。
 */
@Data
public class Result {

    /** 业务处理结果：1 表示成功；失败时为对应 HTTP 状态码。 */
    private Integer code;

    /** 返回给客户端的提示文本。 */
    private String msg;

    /** 成功时携带的业务数据，失败时通常为 null。 */
    private Object data;

    /**
     * 构造带业务数据的成功响应。
     *
     * @param data 要返回的业务数据
     * @return code 为 1 的成功响应
     */
    public static Result success(Object data) {
        Result result = new Result();
        result.code=200;
        result.setMsg("success");
        result.setData(data);
        return result;
    }

    /**
     * 构造不携带业务数据的成功响应。
     *
     * @return code 为 200 的成功响应
     */
    public static Result success() {
        Result result = new Result();
        result.code=200;
        result.setMsg("success");
        return result;
    }

    /**
     * 构造未知错误的失败响应，默认使用 HTTP 500 作为业务码。
     *
     * @param msg 返回给客户端的失败原因
     * @return code 为 500 的失败响应
     */
    public static Result error(String msg) {
        return error(500, msg);
    }

    /**
     * 构造带指定 HTTP 状态码的失败响应。
     *
     * @param code HTTP 状态码，例如 400、404、409 或 422
     * @param msg 返回给客户端的失败原因
     * @return code 与 HTTP 状态码一致的失败响应
     */
    public static Result error(Integer code, String msg) {
        Result result = new Result();
        result.setCode(code);
        result.setMsg(msg);
        return result;
    }
}
