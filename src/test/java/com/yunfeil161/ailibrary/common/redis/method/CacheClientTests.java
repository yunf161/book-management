package com.yunfeil161.ailibrary.common.redis.method;

import com.yunfeil161.ailibrary.common.util.JsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheClientTests {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private CacheClient cacheClient;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheClient = new CacheClient(redisTemplate);
    }

    @Test
    void returnsCachedObjectWithoutQueryingDatabase() {
        Sample cached = new Sample(1L, "cached");
        when(valueOperations.get("department:1"))
                .thenReturn(JsonUtils.toJson(cached));
        AtomicBoolean databaseCalled = new AtomicBoolean(false);

        Sample result = cacheClient.queryWithPassThrough(
                "department:",
                1L,
                Sample.class,
                id -> {
                    databaseCalled.set(true);
                    return null;
                },
                10L,
                TimeUnit.MINUTES
        );

        assertEquals(cached, result);
        assertFalse(databaseCalled.get());
    }

    @Test
    void returnsNullForCachedEmptyValueWithoutQueryingDatabase() {
        when(valueOperations.get("department:2")).thenReturn("");
        AtomicBoolean databaseCalled = new AtomicBoolean(false);

        Sample result = cacheClient.queryWithPassThrough(
                "department:",
                2L,
                Sample.class,
                id -> {
                    databaseCalled.set(true);
                    return new Sample(id, "database");
                },
                10L,
                TimeUnit.MINUTES
        );

        assertNull(result);
        assertFalse(databaseCalled.get());
    }

    @Test
    void cachesDatabaseNullAsShortLivedEmptyValue() {
        when(valueOperations.get("department:3")).thenReturn(null);

        Sample result = cacheClient.queryWithPassThrough(
                "department:",
                3L,
                Sample.class,
                id -> null,
                10L,
                TimeUnit.MINUTES
        );

        assertNull(result);
        verify(valueOperations).set(
                "department:3",
                "",
                2L,
                TimeUnit.MINUTES
        );
    }

    @Test
    void setsTtlWithinConfiguredRandomRange() {
        cacheClient.set(
                "department:4",
                new Sample(4L, "random-ttl"),
                100L,
                TimeUnit.SECONDS
        );

        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(
                org.mockito.ArgumentMatchers.eq("department:4"),
                org.mockito.ArgumentMatchers.anyString(),
                ttlCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)
        );
        assertTrue(ttlCaptor.getValue() >= 100L);
        assertTrue(ttlCaptor.getValue() <= 110L);
    }

    @Test
    void requiredDeleteRetriesTransientRedisFailure() {
        ReflectionTestUtils.setField(cacheClient, "deleteMaxAttempts", 3);
        ReflectionTestUtils.setField(cacheClient, "deleteRetryDelayMillis", 0L);
        when(redisTemplate.delete("book:1"))
                .thenThrow(new QueryTimeoutException("temporary"))
                .thenReturn(true);

        cacheClient.deleteRequired("book:1");

        verify(redisTemplate, times(2)).delete("book:1");
    }

    @Test
    void requiredDeleteFailsAfterConfiguredRetries() {
        ReflectionTestUtils.setField(cacheClient, "deleteMaxAttempts", 3);
        ReflectionTestUtils.setField(cacheClient, "deleteRetryDelayMillis", 0L);
        when(redisTemplate.delete("book:2"))
                .thenThrow(new QueryTimeoutException("redis unavailable"));

        assertThrows(
                com.yunfeil161.ailibrary.common.exception.ServiceUnavailableException.class,
                () -> cacheClient.deleteRequired("book:2")
        );
        verify(redisTemplate, times(3)).delete("book:2");
    }

    private record Sample(Long id, String name) {
    }
}
