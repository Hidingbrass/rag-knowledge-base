package com.example.aikb.config;

import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * HTTP Client 配置。
 *
 * Spring Boot 调 FastAPI，本质就是一次服务间 HTTP 调用。
 * 这里集中创建 RestClient，避免每个业务类自己 new HTTP 客户端。
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient fastApiRestClient(FastApiProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .withReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings));

        // 如果配置了 FastAPI API Key，在所有请求中自动携带 X-API-Key 头。
        applyInternalApiKey(builder, properties.apiKey());

        return builder.build();
    }

    void applyInternalApiKey(RestClient.Builder builder, String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
    }
}
