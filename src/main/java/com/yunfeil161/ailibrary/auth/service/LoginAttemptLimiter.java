package com.yunfeil161.ailibrary.auth.service;

import com.yunfeil161.ailibrary.common.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 在BCrypt计算前原子预占次数，限制同一邮箱15分钟内连续10次错误尝试。 */
@Service
@RequiredArgsConstructor
public class LoginAttemptLimiter {

  private final StringRedisTemplate redis;
  private static final DefaultRedisScript<Long> RESERVE =
    new DefaultRedisScript<>(
      "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('PEXPIRE',KEYS[1],900000) end; return n;",
      Long.class
    );

  private String key(String email) {
    try {
      return (
        "auth:login:attempts:" +
        HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(
            email.getBytes(StandardCharsets.UTF_8)
          )
        )
      );
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public void reserve(String email) {
    try {
      Long count = redis.execute(RESERVE, List.of(key(email)));
      if (count == null) throw new ServiceUnavailableException(
        "登录限流服务暂不可用"
      );
      if (count > 10) throw new TooManyRequestsException(
        "登录尝试过于频繁，请15分钟后重试"
      );
    } catch (DataAccessException exception) {
      throw new ServiceUnavailableException("登录限流服务暂不可用", exception);
    }
  }

  public void succeeded(String email) {
    try {
      redis.delete(key(email));
    } catch (DataAccessException exception) {
      throw new ServiceUnavailableException("登录限流服务暂不可用", exception);
    }
  }
}
