package com.example.aikb.dto.aicall;

import java.math.BigDecimal;
import java.util.List;

public record AiCallSummaryResponse(
        int hours,
        long totalCalls,
        long successfulCalls,
        long failedCalls,
        double successRate,
        long averageElapsedMs,
        long p95ElapsedMs,
        long totalTokens,
        long totalRetries,
        BigDecimal estimatedCostYuan,
        List<AiOperationSummary> operations
) {
}
