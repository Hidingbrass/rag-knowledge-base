package com.example.aikb.service;

import com.example.aikb.config.AiRateLimitProperties;
import com.example.aikb.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRateLimitServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-07-05T08:30:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Test
    void disabledRateLimitShouldNotTouchRedis() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        AiRateLimitService service = new AiRateLimitService(
                redisTemplate,
                new AiRateLimitProperties(false, 5, 100),
                FIXED_CLOCK
        );

        service.checkAiCallAllowed("user-1", "JOB_ANALYZE");

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void firstAiCallShouldIncrementMinuteAndDayWindows() {
        TestRedis redis = createRedis(1L, 1L);
        AiRateLimitService service = new AiRateLimitService(
                redis.template,
                new AiRateLimitProperties(true, 5, 100),
                FIXED_CLOCK
        );

        service.checkAiCallAllowed("user-1", "JOB_ANALYZE");

        verify(redis.values).increment(contains("rate:ai:minute:user-1:JOB_ANALYZE:202607051630"));
        verify(redis.values).increment(contains("rate:ai:day:user-1:JOB_ANALYZE:20260705"));
        verify(redis.template).expire(contains("rate:ai:minute:user-1:JOB_ANALYZE:202607051630"), any());
        verify(redis.template).expire(contains("rate:ai:day:user-1:JOB_ANALYZE:20260705"), any());
    }

    @Test
    void minuteLimitExceededShouldRejectRequest() {
        TestRedis redis = createRedis(6L);
        AiRateLimitService service = new AiRateLimitService(
                redis.template,
                new AiRateLimitProperties(true, 5, 100),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.checkAiCallAllowed("user-1", "RAG_CHAT"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("AI 接口调用过于频繁");
    }

    @Test
    void dayLimitExceededShouldRejectRequest() {
        TestRedis redis = createRedis(1L, 101L);
        AiRateLimitService service = new AiRateLimitService(
                redis.template,
                new AiRateLimitProperties(true, 5, 100),
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.checkAiCallAllowed("user-1", "RESUME_OPTIMIZE"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("今日 AI 接口调用次数已达上限");
    }

    @Test
    void redisConnectionFailureShouldAllowRequest() {
        TestRedis redis = createRedis();
        when(redis.values.increment(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));
        AiRateLimitService service = new AiRateLimitService(
                redis.template,
                new AiRateLimitProperties(true, 5, 100),
                FIXED_CLOCK
        );

        assertThatCode(() -> service.checkAiCallAllowed("user-1", "JOB_ANALYZE"))
                .doesNotThrowAnyException();
    }

    @SuppressWarnings("unchecked")
    private TestRedis createRedis(Long... counts) {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        if (counts.length > 0) {
            when(valueOperations.increment(anyString())).thenReturn(
                    counts[0],
                    Arrays.copyOfRange(counts, 1, counts.length)
            );
        }
        return new TestRedis(redisTemplate, valueOperations);
    }

    private record TestRedis(
            StringRedisTemplate template,
            ValueOperations<String, String> values
    ) {
    }
}
