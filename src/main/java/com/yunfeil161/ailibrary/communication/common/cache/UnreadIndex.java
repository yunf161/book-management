package com.yunfeil161.ailibrary.communication.common.cache;

import com.yunfeil161.ailibrary.common.exception.ForbiddenException;
import com.yunfeil161.ailibrary.common.exception.NotFoundException;
import com.yunfeil161.ailibrary.communication.message.dto.MessageQueryRequest;
import com.yunfeil161.ailibrary.communication.message.entity.CommunicationMessage;
import com.yunfeil161.ailibrary.communication.message.mapper.MessageMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** 未读索引只是加速层；收件关系和已读状态始终以数据库为准。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnreadIndex {
  private final MessageMapper messages;
  private final StringRedisTemplate redis;
  private final RedissonClient redisson;
  private final TransactionTemplate transactions;

  @Value("${library.communication.namespace:communication}")
  private String namespace;

  private String key(Long memberId) {
    return namespace + ":unread:" + memberId;
  }

  /** 同一成员的重建、已读和消费使用同一把锁，避免延迟任务复活已读索引。 */
  private <T> T coordinated(Long memberId, Supplier<T> work) {
    var lock = redisson.getLock(namespace + ":index-lock:" + memberId);
    boolean acquired = false;
    try {
      acquired = lock.tryLock(3, TimeUnit.SECONDS);
      if (!acquired) throw new IllegalStateException("未读索引繁忙");
      return work.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("未读索引操作中断", e);
    } finally {
      if (acquired && lock.isHeldByCurrentThread()) lock.unlock();
    }
  }

  /** MQ不盲目写入索引：先确认数据库仍未读；Lua同时完成去重和过期设置。 */
  public void add(Long memberId, Long messageId) {
    coordinated(
        memberId,
        () -> {
          var recipient = messages.recipient(messageId, memberId);
          if (recipient == null || recipient.getReadAt() != null) return null;
          var message = messages.find(messageId);
          String script =
              "if redis.call('EXISTS',KEYS[2])==1 or redis.call('ZCARD',KEYS[1])>=500 then return 0"
                  + " end redis.call('ZADD',KEYS[1],ARGV[1],ARGV[2]);"
                  + " redis.call('EXPIRE',KEYS[1],3600); return 1";
          redis.execute(
              new DefaultRedisScript<>(script, Long.class),
              List.of(key(memberId), key(memberId) + ":read:" + messageId),
              String.valueOf(
                  message.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()),
              messageId.toString());
          return null;
        });
  }

  /** 缺少索引或复杂筛选直接查库；简单未读查询在成员锁内重建，避免部分索引误当全集。 */
  public List<CommunicationMessage> queryIfSimple(Long memberId, MessageQueryRequest q) {
    if (!"INBOX".equals(q.getBox())
        || !Boolean.FALSE.equals(q.getRead())
        || q.getMessageType() != null
        || q.getBusinessType() != null
        || q.getBusinessId() != null
        || q.getId() != null
        || q.getConversationId() != null
        || q.getSenderMemberId() != null
        || q.getRecipientMemberId() != null
        || q.getSendType() != null
        || q.getSubject() != null
        || q.getCreatedFrom() != null
        || q.getCreatedTo() != null
        || q.getAfterSequence() != null) {
      return null;
    }
    try {
      // 比较数据库总量并再次按未读关系取正文，过期/漏写/已读清理失败不会泄露旧结果。
      long count = messages.unreadCount(memberId);
      if (count > 500) return null;
      var ids = redis.opsForZSet().reverseRange(key(memberId), 0, -1);
      if (ids != null && ids.size() == count && count <= 500) {
        var cached =
            ids.isEmpty() ? List.<CommunicationMessage>of() : messages.unreadIds(memberId, ids);
        if (cached.size() == count) return cached;
      }
      var rows = messages.unread(memberId);
      if (rows.size() > 500) return null;
      rebuild(memberId, rows);
      return rows;
    } catch (RuntimeException e) {
      log.warn("未读索引重建失败，使用数据库结果：memberId={}", memberId, e);
      var rows = messages.unread(memberId);
      return rows.size() > 500 ? null : rows;
    }
  }

  public void rebuild(Long memberId, List<CommunicationMessage> ignored) {
    coordinated(
        memberId,
        () -> {
          // 在锁内重新读取，不能将加锁前的旧快照写回Redis。
          var rows = messages.unread(memberId);
          redis.delete(key(memberId));
          if (rows.size() > 500) return null;
          for (var row : rows) {
            redis
                .opsForZSet()
                .add(
                    key(memberId),
                    row.getId().toString(),
                    row.getCreatedAt().toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
          }
          redis.expire(key(memberId), Duration.ofHours(1));
          return null;
        });
  }

  /** 先提交已读关系再清索引；清理失败不撤销已读，后续消费者会依据数据库跳过。 */
  public void read(Long memberId, Long messageId) {
    coordinated(
        memberId,
        () -> {
          transactions.executeWithoutResult(
              status -> {
                if (messages.find(messageId) == null) throw new NotFoundException("消息不存在");
                if (messages.recipient(messageId, memberId) == null)
                  throw new ForbiddenException("不是消息收件人");
                int affected = messages.markRead(messageId, memberId);
                if (affected > 1) throw new IllegalStateException("已读更新影响多条记录");
              });
          try {
            String script =
                "redis.call('ZREM',KEYS[1],ARGV[1]); redis.call('SET',KEYS[2],'1','EX',86400);"
                    + " return 1";
            redis.execute(
                new DefaultRedisScript<>(script, Long.class),
                List.of(key(memberId), key(memberId) + ":read:" + messageId),
                messageId.toString());
          } catch (RuntimeException e) {
            log.warn("已读已提交，索引清理失败：memberId={}, messageId={}", memberId, messageId, e);
          }
          return null;
        });
  }
}
