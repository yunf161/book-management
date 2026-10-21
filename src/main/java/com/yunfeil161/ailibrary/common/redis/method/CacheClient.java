package com.yunfeil161.ailibrary.common.redis.method;

import com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException;
import com.yunfeil161.ailibrary.common.redis.api.redisExpire;
import com.yunfeil161.ailibrary.common.util.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private static final long NULL_CACHE_TTL_MINUTES = 2L;

    private final StringRedisTemplate redisTemplate;

    @Value("${library.cache.delete-max-attempts:3}")
    private int deleteMaxAttempts;

    @Value("${library.cache.delete-retry-delay-millis:50}")
    private long deleteRetryDelayMillis;

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }



    public void set(
            String key,
            Object value,
            Long time,
            TimeUnit unit
    ) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("cache key must not be blank");
        }
        if (time == null || time <= 0) {
            throw new IllegalArgumentException("缓存有效期必须大于 0");
        }
        if (unit == null) {
            throw new IllegalArgumentException("time unit must not be null");
        }

        long randomTime = Math.max(1L, time / 10L);
        long randomOffset = ThreadLocalRandom.current().nextLong(randomTime + 1L);
        long ttl;
        try {
            ttl = Math.addExact(time, randomOffset);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("cache ttl exceeds the Long range", exception);
        }

        try {
            redisTemplate.opsForValue().set(
                    key,
                    JsonUtils.toJson(value),
                    ttl,
                    unit
            );
        } catch (DataAccessException exception) {
            log.warn("Redis缓存写入失败，已跳过缓存：key={}", key, exception);
        }
    }

    /**
     * 在基础有效期上增加 0 到 randomTime（包含边界）的随机时间，
     * 避免大量缓存同时过期。
     */



    public void setWithLogicalExpire(String key,
                                     Object value,
                                     Long time,
                                     TimeUnit unit) {
        redisExpire redisexpire = new redisExpire();
        redisexpire.setData(value);
        redisexpire.setExpireTime(
                LocalDateTime.now().plusNanos(unit.toNanos(time))
        );
        redisTemplate.opsForValue().set(key, JsonUtils.toJson(redisexpire));
    }

    /**
     * 使用缓存空字符串阻止不存在的数据持续访问数据库。解决缓存穿透
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                           ID id,
                                           Class<R> type,
                                           Function<ID, R> dbFallback,
                                           Long time,
                                           TimeUnit unit) {
        String key = keyPrefix + id;
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (DataAccessException exception) {
            log.warn("Redis缓存读取失败，已降级查询数据库：key={}", key, exception);
            return dbFallback.apply(id);
        }

        if (StringUtils.hasText(json)) {
            return JsonUtils.fromJson(json, type);
        }

        if (json != null) {
            return null;
        }

        R result = dbFallback.apply(id);
        // 数据库中不存在该数据，缓存空字符串并设置过期时间
        if (result == null) {
            try {
                redisTemplate.opsForValue().set(
                        key,
                        "",
                        NULL_CACHE_TTL_MINUTES,
                        TimeUnit.MINUTES
                );
            } catch (DataAccessException exception) {
                log.warn("Redis空值缓存写入失败，已跳过缓存：key={}", key, exception);
            }
            return null;
        }

        this.set(key, result, time, unit);
        return result;
    }




    private static final ExecutorService CACHE_REBUILD_EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(10);


    //逻辑过期
    public <R, ID> R queryWithLogicalExpire(String keyPrefix,
                                           ID id,
                                           Class<R> type,
                                           Function<ID, R> dbFallback,
                                           Long time,
                                           TimeUnit unit) {
       String key = keyPrefix+id;
       String json= redisTemplate.opsForValue().get(key);
         if(!StringUtils.hasText(json)){
             return null;
         }
         redisExpire redisexpire=JsonUtils.fromJson(json, redisExpire.class);
         R obj=JsonUtils.fromJson(JsonUtils.toJson(redisexpire.getData()),type);
         LocalDateTime expireTime=redisexpire.getExpireTime();
         if(expireTime.isAfter(LocalDateTime.now())){
             return obj;}

        String lockKey=key+"lock";
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newData = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newData, time, unit);
                } catch (Exception e) {
                    log.error("缓存重建异常", e);
                    throw new ServiceUnavailableException("缓存重建异常", e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        return obj;


    }

    //更新后删除缓存
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            log.warn("Redis缓存删除失败：key={}", key, exception);
        }
    }

    /**
     * 数据修改事务使用的强制删除。短暂故障会按递增间隔重试；最终失败时
     * 抛出异常，使调用方数据库事务回滚，避免数据库新值与旧缓存长期并存。
     */
    public void deleteRequired(String key) {
        DataAccessException last = null;
        int attempts = Math.max(1, deleteMaxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                redisTemplate.delete(key);
                return;
            } catch (DataAccessException exception) {
                last = exception;
                log.warn(
                        "事务内删除缓存失败，准备重试：key={}, attempt={}/{}",
                        key,
                        attempt,
                        attempts,
                        exception
                );
                if (attempt < attempts) {
                    try {
                        Thread.sleep(Math.max(0L, deleteRetryDelayMillis) * attempt);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new ServiceUnavailableException(
                                "删除缓存时线程被中断，数据库操作已回滚",
                                interrupted
                        );
                    }
                }
            }
        }
        throw new ServiceUnavailableException(
                "缓存删除失败，数据库操作已回滚",
                last
        );
    }

    private Boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }

    private void unlock(String key) {
        redisTemplate.delete(key);
    }





}
