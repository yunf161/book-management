package com.yunfeil161.ailibrary.auth.service;

import com.yunfeil161.ailibrary.common.exception.*;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 邮箱验证码的发送限流、错误次数和一次性消费均由Lua原子维护。 */
@Service
@RequiredArgsConstructor
public class VerificationCodeStore {

  private static final int MAX_FAILURES = 5;
  private final StringRedisTemplate redis;

  private static final DefaultRedisScript<Long> ISSUE =
    new DefaultRedisScript<>(
      "if not redis.call('SET',KEYS[2],ARGV[2],'NX','PX',60000) then return 0 end;" +
        "redis.call('PSETEX',KEYS[1],600000,ARGV[1]); redis.call('DEL',KEYS[3]); return 1;",
      Long.class
    );

  private static final DefaultRedisScript<Long> CONSUME =
    new DefaultRedisScript<>(
      "if tonumber(redis.call('GET',KEYS[3]) or '0')>=tonumber(ARGV[2]) then return -1 end;" +
        "local code=redis.call('GET',KEYS[1]); if not code then return 0 end;" +
        "if code==ARGV[1] then redis.call('DEL',KEYS[1],KEYS[2],KEYS[3]); return 1 end;" +
        "local failures=redis.call('INCR',KEYS[3]); local ttl=redis.call('PTTL',KEYS[1]);" +
        "if ttl<1 then ttl=600000 end; redis.call('PEXPIRE',KEYS[3],ttl);" +
        "if failures>=tonumber(ARGV[2]) then redis.call('DEL',KEYS[1]); return -1 end; return 0;",
      Long.class
    );

  private static final DefaultRedisScript<Long> REVOKE_FAILED_SEND =
    new DefaultRedisScript<>(
      "if redis.call('GET',KEYS[1])==ARGV[1] and redis.call('GET',KEYS[2])==ARGV[2] then " +
        "return redis.call('DEL',KEYS[1],KEYS[2],KEYS[3]) end; return 0;",
      Long.class
    );

  private List<String> keys(String email) {
    return List.of(
      "auth:register:code:" + email,
      "auth:register:rate:" + email,
      "auth:register:attempts:" + email
    );
  }

  public String issue(String email, String code) {
    String nonce = UUID.randomUUID().toString();
    if (
      execute(ISSUE, email, code, nonce) != 1
    ) throw new TooManyRequestsException("验证码发送过于频繁");
    return nonce;
  }

  /** 成功验证即消费；数据库后续失败也不恢复旧验证码，用户需要重新获取。 */
  public void consume(String email, String code) {
    long result = execute(CONSUME, email, code, String.valueOf(MAX_FAILURES));
    if (result < 0) throw new TooManyRequestsException(
      "验证码错误次数过多，请重新获取验证码"
    );
    if (result != 1) throw new UnprocessableEntityException(
      "验证码错误或已过期"
    );
  }

  /** SMTP迟到失败不能删除另一次发送已经写入的新验证码。 */
  public void revokeFailedSend(String email, String code, String nonce) {
    execute(REVOKE_FAILED_SEND, email, code, nonce);
  }

  private long execute(
    DefaultRedisScript<Long> script,
    String email,
    String... arguments
  ) {
    try {
      Long result = redis.execute(script, keys(email), (Object[]) arguments);
      if (result == null) throw new ServiceUnavailableException(
        "验证码服务暂不可用"
      );
      return result;
    } catch (DataAccessException exception) {
      throw new ServiceUnavailableException("验证码服务暂不可用", exception);
    }
  }
}
