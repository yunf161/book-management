package com.yunfeil161.ailibrary.book.borrow.service.impl;

import com.yunfeil161.ailibrary.book.borrow.entity.BorrowApplication;
import com.yunfeil161.ailibrary.book.borrow.enums.BorrowApplicationType;
import com.yunfeil161.ailibrary.common.exception.ConflictException;
import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import com.yunfeil161.ailibrary.common.redis.method.CacheClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 使用 Redis 原子占位判断同一资源是否已经存在待审批申请。
 *
 * <p>普通借阅以“成员 + 图书”为唯一维度，续借以“成员 + 借阅记录”为唯一维度。
 * Redis 是提交阶段的快速判定入口；申请离开 PENDING 状态时必须删除对应占位。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingBorrowApplicationGuard {

  /** 数据库插入前的临时占位只保留一分钟，进程崩溃后可自动释放。 */
  private static final long RESERVATION_TTL_SECONDS = 60L;
  private static final String BORROW_PREFIX = "book:borrow:pending:member:";
  private static final String RENEW_PREFIX = "book:renew:pending:member:";

  private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
    new DefaultRedisScript<>(
      "if redis.call('exists', KEYS[1]) == 1 then return 0 end " +
      "redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[2]) return 1",
      Long.class
    );

  private static final DefaultRedisScript<Long> REPLACE_IF_OWNED_SCRIPT =
    new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end " +
      "redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]) return 1",
      Long.class
    );

  private static final DefaultRedisScript<Long> DELETE_IF_OWNED_SCRIPT =
    new DefaultRedisScript<>(
      "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end " +
      "return redis.call('del', KEYS[1])",
      Long.class
    );

  private final StringRedisTemplate redisTemplate;
  private final CacheClient cacheClient;

  /** 不同数据库环境共享Redis时使用独立命名空间，避免相同自增ID互相碰撞。 */
  @Value("${spring.datasource.url:default}")
  private String datasourceUrl;

  /** 测试或多租户环境可以显式覆盖；生产默认由稳定的数据源URL生成。 */
  @Value("${library.borrow.pending-key-namespace:}")
  private String configuredNamespace;

  /** 原子检查并占位；返回的随机令牌只能释放本次请求创建的占位。 */
  public Reservation reserve(BorrowApplication application) {
    String key = key(application);
    String token = UUID.randomUUID().toString();
    long ttlSeconds = Math.max(
      1L,
      Duration.between(LocalDateTime.now(), application.getExpiresAt()).getSeconds()
    );
    try {
      Long result = redisTemplate.execute(
        RESERVE_SCRIPT,
        List.of(key),
        token,
        Long.toString(RESERVATION_TTL_SECONDS)
      );
      if (!Long.valueOf(1L).equals(result)) {
        throw new ConflictException(duplicateMessage(application.getApplicationType()));
      }
      return new Reservation(key, token, ttlSeconds);
    } catch (DataAccessException exception) {
      throw new ServiceUnavailableException("待审批申请校验暂时不可用", exception);
    }
  }

  /**
   * 插入数据库并取得申请ID后，将随机令牌替换成申请ID。
   * 若外层数据库事务最终回滚，事务回调只删除仍属于该申请的占位。
   */
  public void confirm(Reservation reservation, Long applicationId) {
    String value = applicationId.toString();
    try {
      Long result = redisTemplate.execute(
        REPLACE_IF_OWNED_SCRIPT,
        List.of(reservation.key()),
        reservation.token(),
        value,
        Long.toString(reservation.applicationTtlSeconds())
      );
      if (!Long.valueOf(1L).equals(result)) {
        throw new ServiceUnavailableException("待审批申请占位确认失败");
      }
    } catch (DataAccessException exception) {
      release(reservation);
      throw new ServiceUnavailableException("待审批申请占位确认失败", exception);
    }

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
              deleteIfOwned(reservation.key(), value);
            }
          }
        }
      );
    }
  }

  /** 数据库插入前后发生异常时，只撤销本次请求持有的临时占位。 */
  public void release(Reservation reservation) {
    deleteIfOwned(reservation.key(), reservation.token());
  }

  /** 申请审批、拒绝、取消或过期后删除待审批标记。 */
  public void clear(BorrowApplication application) {
    cacheClient.deleteRequired(key(application));
    /*
     * 删除发生在数据库事务提交前。如果提交最终失败，数据库中的申请仍为
     * PENDING，因此必须在事务回滚回调中恢复标记，避免产生重复申请窗口。
     */
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != TransactionSynchronization.STATUS_COMMITTED) {
              try {
                restore(application);
              } catch (RuntimeException exception) {
                log.error(
                  "数据库事务回滚后恢复待审批申请Redis占位失败：applicationId={}",
                  application.getId(),
                  exception
                );
              }
            }
          }
        }
      );
    }
  }

  /** 启动恢复使用：根据数据库中的待审批申请补回Redis标记。 */
  public void restore(BorrowApplication application) {
    long ttlSeconds = Duration.between(
      LocalDateTime.now(),
      application.getExpiresAt()
    ).getSeconds();
    if (ttlSeconds <= 0) return;
    try {
      redisTemplate.opsForValue().setIfAbsent(
        key(application),
        application.getId().toString(),
        Duration.ofSeconds(ttlSeconds)
      );
    } catch (DataAccessException exception) {
      throw new ServiceUnavailableException("恢复待审批申请索引失败", exception);
    }
  }

  String key(BorrowApplication application) {
    String namespace = configuredNamespace == null || configuredNamespace.isBlank()
      ? Integer.toHexString(datasourceUrl.hashCode())
      : configuredNamespace;
    if (application.getApplicationType() == BorrowApplicationType.RENEW) {
      return RENEW_PREFIX + namespace + ":" + application.getMemberId() + ":record:" +
        application.getBorrowRecordId();
    }
    return BORROW_PREFIX + namespace + ":" + application.getMemberId() + ":book:" +
      application.getBookId();
  }

  private void deleteIfOwned(String key, String value) {
    try {
      redisTemplate.execute(DELETE_IF_OWNED_SCRIPT, List.of(key), value);
    } catch (DataAccessException exception) {
      log.error("撤销待审批申请Redis占位失败：key={}", key, exception);
    }
  }

  private String duplicateMessage(BorrowApplicationType type) {
    return type == BorrowApplicationType.RENEW
      ? "该借阅记录已存在待审批续借申请"
      : "已存在相同图书的待审批申请";
  }

  public record Reservation(
    String key,
    String token,
    long applicationTtlSeconds
  ) {}
}
