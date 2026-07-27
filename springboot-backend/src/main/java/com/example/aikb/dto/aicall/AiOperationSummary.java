package com.example.aikb.dto.aicall;

public record AiOperationSummary(
        String businessType,
        long totalCalls,
        long successfulCalls,
        double successRate,
        long p95ElapsedMs,
        long totalTokens
) {
}
