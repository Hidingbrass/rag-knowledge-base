package com.example.aikb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Spring 与 FastAPI 共享的非 RAG 路由最低置信度。 */
@ConfigurationProperties(prefix = "ai.intent-routing")
public record IntentRoutingProperties(double minConfidence) {
    public IntentRoutingProperties {
        if (minConfidence < 0.0 || minConfidence > 1.0) {
            throw new IllegalArgumentException("意图分类最低置信度必须在 0 到 1 之间");
        }
    }
}
