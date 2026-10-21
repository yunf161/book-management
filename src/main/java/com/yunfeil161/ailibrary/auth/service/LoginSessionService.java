package com.yunfeil161.ailibrary.auth.service;

import com.yunfeil161.ailibrary.auth.session.LoginSession;

import java.time.Duration;

/**
 * 登录会话的 Redis 存取服务。
 */
public interface LoginSessionService {
    void save(String jti, LoginSession session, Duration duration);

    LoginSession find(String jti);

    void expire(String jti, Duration duration);

    void delete(String jti);

    /** 账号、任职或角色发生安全变更后撤销该成员的全部登录会话。 */
    void deleteAllForMember(Long memberId);

    /** 读取旧 JTI 在刷新限制窗口内已经签发的新 JWT。 */
    String findRefreshedToken(String oldJti);

    /** 保存旧 JTI 的刷新结果；同一个旧 JTI 在窗口内只保留第一次结果。 */
    boolean saveRefreshedToken(String oldJti, String newToken, Duration duration);

    /** 原子保存新会话、刷新映射并缩短旧会话的宽限期。 */
    boolean rotate(
            String oldJti,
            String newToken,
            LoginSession newSession,
            Duration sessionDuration,
            Duration refreshResultDuration,
            Duration oldSessionGrace
    );
}
