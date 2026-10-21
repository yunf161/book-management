package com.yunfeil161.ailibrary.auth.session;

import com.yunfeil161.ailibrary.common.exception.UnauthorizedException;

/**
 * 保存当前请求已经通过校验的登录会话。
 */
public final class LoginUserContext {
    private static final ThreadLocal<LoginSession> CONTEXT = new ThreadLocal<>();

    private LoginUserContext() {
    }

    public static void set(LoginSession session) {
        CONTEXT.set(session);
    }

    public static LoginSession get() {
        LoginSession session = CONTEXT.get();
        if (session == null) {
            throw new UnauthorizedException("未获取到当前登录用户");
        }
        return session;
    }

    /** 锁键等可选认证场景读取上下文时使用；未登录返回 null。 */
    public static LoginSession getNullable() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
