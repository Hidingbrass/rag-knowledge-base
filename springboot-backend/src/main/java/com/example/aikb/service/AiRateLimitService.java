package com.example.aikb.service;

import com.example.aikb.config.AiRateLimitProperties;
import com.example.aikb.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * 基于 Redis 的 AI 接口限流。
 *
 * 实现方式是固定窗口计数：
 * - 分钟窗口：控制短时间连续点击；
 * - 天窗口：控制单个用户每天总调用成本。
 */
@Service
public class AiRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(AiRateLimitService.class);
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final StringRedisTemplate redisTemplate;
    private final AiRateLimitProperties properties;
    private final Clock clock;

    @Autowired
    public AiRateLimitService(StringRedisTemplate redisTemplate, AiRateLimitProperties properties) {
        this(redisTemplate, properties, Clock.systemDefaultZone());
    }

    AiRateLimitService(StringRedisTemplate redisTemplate, AiRateLimitProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    public void checkAiCallAllowed(String userId, String businessType) {
        if (!properties.enabled()) {
            return;
        }

        String normalizedUserId = normalize(userId);
        String normalizedBusinessType = normalize(businessType);

        try {
            checkWindow(
                    "minute",
                    minuteKey(normalizedUserId, normalizedBusinessType),
                    properties.perMinute(),
                    Duration.ofMinutes(2),
                    "AI 接口调用过于频繁，请稍后再试"
            );
            checkWindow(
                    "day",
                    dayKey(normalizedUserId, normalizedBusinessType),
                    properties.perDay(),
                    Duration.ofDays(2),
                    "今日 AI 接口调用次数已达上限"
            );
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.warn(
                    "Redis AI rate limit check failed, allow request. userId={}, businessType={}, message={}",
                    normalizedUserId,
                    normalizedBusinessType,
                    exception.getMessage()
            );
        }
    }

    private void checkWindow(String windowName, String key, int limit, Duration ttl, String message) {
        if (limit <= 0) {
            return;
        }

        Long count = redisTemplate.opsForValue().increment(key);
        if (Objects.equals(count, 1L)) {
            redisTemplate.expire(key, ttl);
        }

        if (count != null && count > limit) {
            log.info(
                    "AI rate limit exceeded. window={}, key={}, count={}, limit={}",
                    windowName,
                    key,
                    count,
                    limit
            );
            throw new RateLimitExceededException(message);
        }
    }

    private String minuteKey(String userId, String businessType) {
        String minute = MINUTE_FORMATTER.format(clock.instant().atZone(clock.getZone()));
        return "rate:ai:minute:%s:%s:%s".formatted(userId, businessType, minute);
    }

    private String dayKey(String userId, String businessType) {
        String day = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE);
        return "rate:ai:day:%s:%s:%s".formatted(userId, businessType, day);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "anonymous";
        }
        return value.trim().replaceAll("[^a-zA-Z0-9_.:-]", "_");
    }
}
