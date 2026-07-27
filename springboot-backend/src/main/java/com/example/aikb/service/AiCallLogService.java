package com.example.aikb.service;

import com.example.aikb.entity.AiCallLog;
import com.example.aikb.dto.aicall.AiCallSummaryResponse;
import com.example.aikb.dto.aicall.AiOperationSummary;
import com.example.aikb.dto.fastapi.FastApiModelUsage;
import com.example.aikb.repository.AiCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiCallLogService {

    private static final Logger log = LoggerFactory.getLogger(AiCallLogService.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final AiCallLogRepository repository;

    public AiCallLogService(AiCallLogRepository repository) {
        this.repository = repository;
    }

    public void recordFastApiCall(
            String businessType,
            String endpoint,
            boolean success,
            long elapsedMs,
            String errorMessage
    ) {
        recordFastApiCall(
                businessType,
                endpoint,
                success,
                elapsedMs,
                errorMessage,
                null
        );
    }

    public void recordFastApiCall(
            String businessType,
            String endpoint,
            boolean success,
            long elapsedMs,
            String errorMessage,
            FastApiModelUsage usage
    ) {
        try {
            repository.save(new AiCallLog(
                    UUID.randomUUID(),
                    "FASTAPI",
                    businessType,
                    endpoint,
                    success,
                    elapsedMs,
                    truncate(errorMessage),
                    usage == null ? null : String.join(",", usage.models()),
                    usage == null ? 0 : usage.upstreamCallCount(),
                    usage == null ? 0 : usage.retryCount(),
                    usage == null ? 0 : usage.promptTokens(),
                    usage == null ? 0 : usage.completionTokens(),
                    usage == null ? 0 : usage.totalTokens(),
                    usage == null || usage.estimatedCostYuan() == null
                            ? BigDecimal.ZERO
                            : usage.estimatedCostYuan(),
                    Instant.now()
            ));
        } catch (RuntimeException exception) {
            log.warn("Failed to save AI call log: businessType={}, endpoint={}", businessType, endpoint, exception);
        }
    }

    public List<AiCallLog> listRecentLogs() {
        return repository.findTop20ByOrderByCreatedAtDesc();
    }

    public AiCallSummaryResponse summarize(int hours) {
        int safeHours = Math.min(24 * 30, Math.max(1, hours));
        List<AiCallLog> logs = repository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                Instant.now().minus(safeHours, ChronoUnit.HOURS)
        );
        long total = logs.size();
        long successful = logs.stream().filter(AiCallLog::success).count();
        long elapsedTotal = logs.stream().mapToLong(AiCallLog::elapsedMs).sum();
        long totalTokens = logs.stream().mapToLong(AiCallLog::totalTokens).sum();
        long totalRetries = logs.stream().mapToLong(AiCallLog::retryCount).sum();
        BigDecimal cost = logs.stream()
                .map(AiCallLog::estimatedCostYuan)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, List<AiCallLog>> byOperation = new LinkedHashMap<>();
        logs.stream()
                .sorted(Comparator.comparing(AiCallLog::businessType))
                .forEach(log -> byOperation.computeIfAbsent(
                        log.businessType(),
                        ignored -> new ArrayList<>()
                ).add(log));
        List<AiOperationSummary> operations = byOperation.entrySet().stream()
                .map(entry -> {
                    List<AiCallLog> operationLogs = entry.getValue();
                    long operationSuccess = operationLogs.stream()
                            .filter(AiCallLog::success)
                            .count();
                    return new AiOperationSummary(
                            entry.getKey(),
                            operationLogs.size(),
                            operationSuccess,
                            rate(operationSuccess, operationLogs.size()),
                            percentile95(operationLogs),
                            operationLogs.stream().mapToLong(AiCallLog::totalTokens).sum()
                    );
                })
                .toList();

        return new AiCallSummaryResponse(
                safeHours,
                total,
                successful,
                total - successful,
                rate(successful, total),
                total == 0 ? 0 : Math.round((double) elapsedTotal / total),
                percentile95(logs),
                totalTokens,
                totalRetries,
                cost,
                operations
        );
    }

    private double rate(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return Math.round(((double) numerator / denominator) * 10_000.0) / 10_000.0;
    }

    private long percentile95(List<AiCallLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }
        List<Long> values = logs.stream()
                .map(AiCallLog::elapsedMs)
                .sorted()
                .toList();
        int index = (int) Math.ceil(values.size() * 0.95) - 1;
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }
}
