package com.example.aikb.dto.aicall;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record AiCallLogResponse(
        UUID id,
        String provider,
        String businessType,
        String endpoint,
        boolean success,
        long elapsedMs,
        String errorMessage,
        String modelNames,
        int upstreamCallCount,
        int retryCount,
        long promptTokens,
        long completionTokens,
        long totalTokens,
        BigDecimal estimatedCostYuan,
        Instant createdAt
) {
}
