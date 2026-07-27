package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * FastAPI 汇总的一次业务请求内所有模型调用用量。
 */
public record FastApiModelUsage(
        List<String> models,
        @JsonProperty("upstream_call_count")
        int upstreamCallCount,
        @JsonProperty("retry_count")
        int retryCount,
        @JsonProperty("prompt_tokens")
        long promptTokens,
        @JsonProperty("completion_tokens")
        long completionTokens,
        @JsonProperty("total_tokens")
        long totalTokens,
        @JsonProperty("estimated_cost_yuan")
        BigDecimal estimatedCostYuan
) {
    public static FastApiModelUsage empty() {
        return new FastApiModelUsage(List.of(), 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }
}
