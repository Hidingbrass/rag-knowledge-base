package com.example.aikb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FastAPI AI 服务配置。
 *
 * 这些字段来自 application.yml 的 fastapi.*。
 * 好处是：FastAPI 地址、超时时间和默认 RAG 参数都可以通过配置修改，
 * 不需要写死在 Controller 或 Client 代码里。
 */
@ConfigurationProperties(prefix = "fastapi")
public record FastApiProperties(
        String baseUrl,
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int defaultCandidateK,
        int defaultRerankTopK,
        double defaultRerankMinScore,
        String defaultRetrievalMode,
        int defaultKeywordLimit
) {
}
