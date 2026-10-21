package com.yunfeil161.ailibrary.config.security.jwt;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类，负责生成和解析（校验）登录令牌。
 *
 * <p>该类交给 Spring 管理，因此其他类可以通过构造器注入后直接使用。
 * 密钥与有效期来自 application.yml，不在业务代码中写死。</p>
 */
@Component
public class JwtUtils {

    /** HMAC 签名和验签使用的同一把对称密钥。 */
    private final SecretKey secretKey;

    /** 令牌有效时长，单位为毫秒。 */
    private final long expirationMillis;

    /**
     * 已配置好验签密钥的解析器。
     * JwtParser 构建完成后可以安全复用，不必每次解析令牌都重新创建。
     */
    private final JwtParser jwtParser;

    /**
     * Spring 创建该工具类时，将 application.yml 中的 JWT 配置注入进来。
     *
     * @param secret Base64 编码的密钥字符串
     * @param expirationMillis 令牌有效时长（毫秒）
     */
    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expiration-millis}") long expirationMillis) {
        // 配置错误时让项目启动失败，避免用空密钥生成不安全的令牌。
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("JWT密钥不能为空");
        }
        if (expirationMillis <= 0) {
            throw new IllegalArgumentException("JWT有效期必须大于0");
        }

        // 配置文件保存的是 Base64 文本，先还原成原始字节，再创建 HMAC 密钥。
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);

        this.expirationMillis = expirationMillis;

        // verifyWith 指定验签密钥。解析时会同时检查签名是否合法以及令牌是否过期。
        this.jwtParser = Jwts.parser()
                .verifyWith(secretKey)
                .build();
    }

    /**
     * 生成一个带签名的 JWT。
     *
     * @param claims 需要保存在令牌载荷中的业务数据，例如员工 id、username
     * @return 三段式 JWT 字符串：header.payload.signature
     */
    public String generateToken(Map<String, ?> claims) {
        return generateToken(claims, null);
    }

    /**
     * 生成带有标准 JWT ID 的登录令牌。
     *
     * @param claims 业务载荷
     * @param jti Redis 登录会话的唯一标识
     * @return 已签名的 JWT
     */
    public String generateToken(Map<String, ?> claims, String jti) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                // 自定义载荷，例如 id 和 username。
                .claims(claims)
                // iat：令牌签发时间。
                .issuedAt(Date.from(now))
                // exp：令牌过期时间，超过该时间后解析器会拒绝令牌。
                .expiration(Date.from(now.plusMillis(expirationMillis)));

        if (StringUtils.hasText(jti)) {
            builder.id(jti);
        }

        return builder
                // 使用 secretKey 生成签名，防止载荷被客户端篡改。
                .signWith(secretKey)
                // 将 header、payload、signature 压缩成最终字符串。
                .compact();
    }

    /**
     * 返回与 JWT 有效期相同的 Redis 会话有效期。
     *
     * @return 登录会话有效期
     */
    public Duration getExpirationDuration() {
        return Duration.ofMillis(expirationMillis);
    }

    /**
     * 验证并解析 JWT。
     *
     * <p>只有签名正确、格式正确并且尚未过期的令牌才能返回 Claims。</p>
     *
     * @param token 客户端提交的原始 JWT 字符串，不包含 Bearer 前缀
     * @return 经过签名验证的载荷数据
     * @throws IllegalArgumentException 令牌为空、格式错误、签名错误或已经过期
     */
    public Claims parseToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("令牌不能为空");
        }

        try {
            // parseSignedClaims 会验签并校验 exp，getPayload 获取可信的载荷。
            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            // 隐藏第三方库异常细节，对项目其他层统一暴露业务异常。
            throw new IllegalArgumentException("令牌无效或已过期", e);
        }
    }
}
