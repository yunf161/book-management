package com.yunfeil161.ailibrary.config.security.jwt;

import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.auth.session.LoginUserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;

/**
 * 统一识别登录状态，并在 JWT 即将过期时轮换令牌。
 *
 * <p>该拦截器处理所有请求，但不会拒绝未登录请求。它只负责验证 JWT、读取 Redis
 * 会话并将可信的登录会话写入当前请求；接口是否必须登录由下一层拦截器决定。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenRefreshInterceptor implements HandlerInterceptor {
    public static final String REFRESHED_TOKEN_HEADER = "X-Refreshed-Token";
    public static final String AUTHENTICATED_SESSION_ATTRIBUTE =
            TokenRefreshInterceptor.class.getName() + ".authenticatedSession";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final LoginSessionService loginSessionService;
    private final RedissonClient redissonClient;

    @Value("${jwt.refresh-threshold-millis:1800000}")
    private long refreshThresholdMillis;

    @Value("${jwt.refresh-grace-millis:30000}")
    private long refreshGraceMillis;

    @Value("${jwt.refresh-result-millis:60000}")
    private long refreshResultMillis;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractToken(request);
        if (token == null) {
            return true;
        }

        try {
            Claims claims = jwtUtils.parseToken(token);
            String jti = claims.getId();
            if (!StringUtils.hasText(jti)) {
                return true;
            }

            LoginSession session = loginSessionService.find(jti);
            if (session == null) {
                return true;
            }
            // 退役成员即使残留旧会话，也不再认证或换发 JWT。
            if (session.getMemberType() == com.yunfeil161.ailibrary.common.enums.MemberType.AI_LIBRARIAN) {
                return true;
            }

            LoginSession authenticatedSession = session;
            long remainingMillis = claims.getExpiration().getTime()
                    - System.currentTimeMillis();
            if (remainingMillis <= refreshThresholdMillis) {
                authenticatedSession = refreshToken(jti, session, response);
            }

            LoginUserContext.set(authenticatedSession);
            request.setAttribute(
                    AUTHENTICATED_SESSION_ATTRIBUTE,
                    authenticatedSession
            );
        } catch (ServiceUnavailableException | ConflictException exception) {
            // Redis或刷新锁故障属于服务端问题，不能伪装成凭证失效。
            throw exception;
        } catch (IllegalArgumentException ignored) {
            // JWT格式、签名或有效期错误时保持匿名，由第二层统一返回401。
        }

        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization)
                || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private LoginSession refreshToken(String oldJti,
                                      LoginSession oldSession,
                                      HttpServletResponse response) {
        String existingToken = loginSessionService.findRefreshedToken(oldJti);
        if (StringUtils.hasText(existingToken)) {
            return reuseRefreshedToken(existingToken, response);
        }

        RLock lock = redissonClient.getLock("lock:auth:refresh:" + oldJti);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(3000, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!acquired) throw new ConflictException("令牌正在刷新，请稍后重试");

            // 双重检查确保等待锁期间已经完成的刷新结果被直接复用。
            existingToken = loginSessionService.findRefreshedToken(oldJti);
            if (StringUtils.hasText(existingToken)) {
                return reuseRefreshedToken(existingToken, response);
            }

            return issueRefreshedToken(oldJti, oldSession, response);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ConflictException("等待令牌刷新时被中断，请重试");
        } catch (RedisException exception) {
            throw new ServiceUnavailableException(
                    "令牌刷新并发控制服务暂时不可用，请稍后重试",
                    exception
            );
        } finally {
            try {
                if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
            } catch (RedisException exception) {
                // 刷新结果已经由Lua原子写入，释放锁失败不能把成功响应改成500。
                org.slf4j.LoggerFactory.getLogger(TokenRefreshInterceptor.class)
                        .error("释放令牌刷新锁失败：oldJti={}", oldJti, exception);
            }
        }
    }

    private LoginSession issueRefreshedToken(String oldJti,
                                             LoginSession oldSession,
                                             HttpServletResponse response) {
        String newJti = UUID.randomUUID().toString();
        LoginSession newSession = new LoginSession(
                newJti,
                oldSession.getMemberId(),
                oldSession.getRoleId(),
                oldSession.getMemberType(),
                oldSession.getRoleCode(),
                oldSession.getEmail(),
                oldSession.getName()
        );
        String newToken = jwtUtils.generateToken(
                Map.of(
                        "memberId", newSession.getMemberId(),
                        "roleId", newSession.getRoleId(),
                        "memberType", newSession.getMemberType(),
                        "roleCode", newSession.getRoleCode()
                ),
                newJti
        );

        boolean rotated = loginSessionService.rotate(
                oldJti,
                newToken,
                newSession,
                jwtUtils.getExpirationDuration(),
                Duration.ofMillis(refreshResultMillis),
                Duration.ofMillis(refreshGraceMillis)
        );
        if (!rotated) {
            String existing = loginSessionService.findRefreshedToken(oldJti);
            if (StringUtils.hasText(existing)) {
                return reuseRefreshedToken(existing, response);
            }
            throw new ConflictException("令牌刷新状态已变化，请重试");
        }
        response.setHeader(REFRESHED_TOKEN_HEADER, newToken);
        return newSession;
    }

    /** 同一分钟内复用第一次刷新生成的 JWT 及其 Redis 会话。 */
    private LoginSession reuseRefreshedToken(String token,
                                             HttpServletResponse response) {
        Claims claims = jwtUtils.parseToken(token);
        LoginSession session = loginSessionService.find(claims.getId());
        if (session == null) {
            throw new ConflictException("刷新后的登录会话已失效，请重新登录");
        }
        response.setHeader(REFRESHED_TOKEN_HEADER, token);
        return session;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        LoginUserContext.clear();
    }
}

