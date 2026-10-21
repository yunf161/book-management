package com.yunfeil161.ailibrary.auth.service.impl;

import com.yunfeil161.ailibrary.auth.service.LoginSessionService;
import com.yunfeil161.ailibrary.auth.session.LoginSession;
import com.yunfeil161.ailibrary.common.enums.MemberType;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** 使用 Lua 原子维护登录会话、成员会话索引和令牌刷新结果。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoginSessionServiceImpl implements LoginSessionService {

  private static final String SESSION_PREFIX = "auth:session:";
  private static final String MEMBER_SESSION_PREFIX = "auth:member-sessions:";
  private static final String REFRESH_PREFIX = "auth:token-refresh:";

  private static final DefaultRedisScript<Long> SAVE_SCRIPT =
    new DefaultRedisScript<>(
      "redis.call('HSET',KEYS[1]," +
      "'jti',ARGV[1],'memberId',ARGV[2],'roleId',ARGV[3]," +
      "'memberType',ARGV[4],'roleCode',ARGV[5],'email',ARGV[6],'name',ARGV[7]);" +
      "redis.call('PEXPIRE',KEYS[1],ARGV[8]);" +
      "redis.call('SADD',KEYS[2],ARGV[1]);" +
      "redis.call('PEXPIRE',KEYS[2],ARGV[8]); return 1;",
      Long.class
    );

  private static final DefaultRedisScript<Long> DELETE_SCRIPT =
    new DefaultRedisScript<>(
      "local memberId=redis.call('HGET',KEYS[1],'memberId');" +
      "redis.call('DEL',KEYS[1]);" +
      "if memberId then local memberKey=ARGV[1]..memberId;" +
      "redis.call('SREM',memberKey,ARGV[2]);" +
      "if redis.call('SCARD',memberKey)==0 then redis.call('DEL',memberKey); end; end;" +
      "return 1;",
      Long.class
    );

  private static final DefaultRedisScript<Long> DELETE_ALL_SCRIPT =
    new DefaultRedisScript<>(
      "local ids=redis.call('SMEMBERS',KEYS[1]);" +
      "for _,id in ipairs(ids) do redis.call('DEL',ARGV[1]..id); end;" +
      "redis.call('DEL',KEYS[1]); return #ids;",
      Long.class
    );

  private static final DefaultRedisScript<Long> ROTATE_SCRIPT =
    new DefaultRedisScript<>(
      "if redis.call('EXISTS',KEYS[4])==0 then return -1; end;" +
      "if redis.call('EXISTS',KEYS[3])==1 then return 0; end;" +
      "redis.call('HSET',KEYS[1],'jti',ARGV[1],'memberId',ARGV[2]," +
      "'roleId',ARGV[3],'memberType',ARGV[4],'roleCode',ARGV[5]," +
      "'email',ARGV[6],'name',ARGV[7]);" +
      "redis.call('PEXPIRE',KEYS[1],ARGV[8]);" +
      "redis.call('SADD',KEYS[2],ARGV[1]);" +
      "redis.call('PEXPIRE',KEYS[2],ARGV[8]);" +
      "redis.call('PSETEX',KEYS[3],ARGV[9],ARGV[10]);" +
      "if redis.call('EXISTS',KEYS[4])==1 then redis.call('PEXPIRE',KEYS[4],ARGV[11]); end;" +
      "return 1;",
      Long.class
    );

  private final StringRedisTemplate redisTemplate;

  @Override
  public void save(String jti, LoginSession session, Duration duration) {
    try {
      redisTemplate.execute(
        SAVE_SCRIPT,
        List.of(SESSION_PREFIX + jti, MEMBER_SESSION_PREFIX + session.getMemberId()),
        jti,
        String.valueOf(session.getMemberId()),
        String.valueOf(session.getRoleId()),
        String.valueOf(session.getMemberType()),
        safe(session.getRoleCode()),
        safe(session.getEmail()),
        safe(session.getName()),
        String.valueOf(duration.toMillis())
      );
    } catch (DataAccessException exception) {
      throw unavailable("保存登录会话失败", exception);
    }
  }

  @Override
  public LoginSession find(String jti) {
    try {
      Map<Object, Object> values = redisTemplate.opsForHash().entries(SESSION_PREFIX + jti);
      if (values.isEmpty()) return null;
      return new LoginSession(
        jti,
        Long.valueOf((String) values.get("memberId")),
        Long.valueOf((String) values.get("roleId")),
        MemberType.valueOf((String) values.get("memberType")),
        (String) values.get("roleCode"),
        (String) values.get("email"),
        (String) values.get("name")
      );
    } catch (DataAccessException exception) {
      throw unavailable("读取登录会话失败", exception);
    }
  }

  @Override
  public void expire(String jti, Duration duration) {
    try {
      redisTemplate.expire(SESSION_PREFIX + jti, duration);
    } catch (DataAccessException exception) {
      throw unavailable("更新登录会话有效期失败", exception);
    }
  }

  @Override
  public void delete(String jti) {
    try {
      redisTemplate.execute(
        DELETE_SCRIPT,
        List.of(SESSION_PREFIX + jti),
        MEMBER_SESSION_PREFIX,
        jti
      );
    } catch (DataAccessException exception) {
      throw unavailable("删除登录会话失败", exception);
    }
  }

  @Override
  public void deleteAllForMember(Long memberId) {
    if (memberId == null) return;
    try {
      redisTemplate.execute(
        DELETE_ALL_SCRIPT,
        List.of(MEMBER_SESSION_PREFIX + memberId),
        SESSION_PREFIX
      );
    } catch (DataAccessException exception) {
      log.error("撤销成员全部登录会话失败：memberId={}", memberId, exception);
      throw unavailable("撤销登录会话失败，请稍后重试", exception);
    }
  }

  @Override
  public String findRefreshedToken(String oldJti) {
    try {
      return redisTemplate.opsForValue().get(REFRESH_PREFIX + oldJti);
    } catch (DataAccessException exception) {
      throw unavailable("读取令牌刷新结果失败", exception);
    }
  }

  @Override
  public boolean saveRefreshedToken(String oldJti, String token, Duration duration) {
    try {
      return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(REFRESH_PREFIX + oldJti, token, duration)
      );
    } catch (DataAccessException exception) {
      throw unavailable("保存令牌刷新结果失败", exception);
    }
  }

  @Override
  public boolean rotate(
    String oldJti,
    String newToken,
    LoginSession session,
    Duration sessionDuration,
    Duration refreshResultDuration,
    Duration oldSessionGrace
  ) {
    try {
      Long result = redisTemplate.execute(
        ROTATE_SCRIPT,
        List.of(
          SESSION_PREFIX + session.getJti(),
          MEMBER_SESSION_PREFIX + session.getMemberId(),
          REFRESH_PREFIX + oldJti,
          SESSION_PREFIX + oldJti
        ),
        session.getJti(),
        String.valueOf(session.getMemberId()),
        String.valueOf(session.getRoleId()),
        String.valueOf(session.getMemberType()),
        safe(session.getRoleCode()),
        safe(session.getEmail()),
        safe(session.getName()),
        String.valueOf(sessionDuration.toMillis()),
        String.valueOf(refreshResultDuration.toMillis()),
        newToken,
        String.valueOf(oldSessionGrace.toMillis())
      );
      return Long.valueOf(1L).equals(result);
    } catch (DataAccessException exception) {
      throw unavailable("原子轮换登录令牌失败", exception);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }

  private ServiceUnavailableException unavailable(String message, Exception cause) {
    return new ServiceUnavailableException(message, cause);
  }
}
