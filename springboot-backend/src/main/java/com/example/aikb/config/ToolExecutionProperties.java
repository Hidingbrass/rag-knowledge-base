package com.example.aikb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 授权工具执行与写操作确认配置。 */
@ConfigurationProperties(prefix = "tools")
public record ToolExecutionProperties(
        int confirmationTtlSeconds,
        Weather weather
) {
    public ToolExecutionProperties {
        if (confirmationTtlSeconds < 30 || confirmationTtlSeconds > 3600) {
            throw new IllegalArgumentException("tools.confirmation-ttl-seconds 必须在 30 到 3600 之间");
        }
        if (weather == null) {
            throw new IllegalArgumentException("tools.weather 配置不能为空");
        }
    }

    public record Weather(
            boolean enabled,
            String geocodingBaseUrl,
            String forecastBaseUrl,
            int connectTimeoutSeconds,
            int readTimeoutSeconds
    ) {
        public Weather {
            if (geocodingBaseUrl == null || geocodingBaseUrl.isBlank()
                    || forecastBaseUrl == null || forecastBaseUrl.isBlank()) {
                throw new IllegalArgumentException("天气工具 URL 不能为空");
            }
            if (connectTimeoutSeconds <= 0 || readTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("天气工具超时必须大于 0");
            }
        }
    }
}
