package com.example.aikb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 接口限流配置。
 *
 * Redis 只保存短期计数，用来保护会调用 FastAPI / 大模型的昂贵接口。
 */
@ConfigurationProperties(prefix = "ai.rate-limit")
public record AiRateLimitProperties(
        boolean enabled,
        int perMinute,
        int perDay
) {
}
