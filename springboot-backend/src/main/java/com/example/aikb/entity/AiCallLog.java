package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "ai_call_log",
        indexes = {
                @Index(name = "idx_ai_call_log_business_created_at", columnList = "business_type,created_at"),
                @Index(name = "idx_ai_call_log_success_created_at", columnList = "success,created_at")
        }
)
public class AiCallLog {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String businessType;

    @Column(nullable = false)
    private String endpoint;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false)
    private long elapsedMs;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private String modelNames;

    @Column(nullable = false)
    private int upstreamCallCount;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private long promptTokens;

    @Column(nullable = false)
    private long completionTokens;

    @Column(nullable = false)
    private long totalTokens;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal estimatedCostYuan;

    @Column(nullable = false)
    private Instant createdAt;

    protected AiCallLog() {
    }

    public AiCallLog(
            UUID id,
            String provider,
            String businessType,
            String endpoint,
            boolean success,
            long elapsedMs,
            String errorMessage,
            Instant createdAt
    ) {
        this(id, provider, businessType, endpoint, success, elapsedMs, errorMessage,
                null, 0, 0, 0, 0, 0, BigDecimal.ZERO, createdAt);
    }

    public AiCallLog(
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
        this.id = id;
        this.provider = provider;
        this.businessType = businessType;
        this.endpoint = endpoint;
        this.success = success;
        this.elapsedMs = elapsedMs;
        this.errorMessage = errorMessage;
        this.modelNames = modelNames;
        this.upstreamCallCount = upstreamCallCount;
        this.retryCount = retryCount;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.estimatedCostYuan = estimatedCostYuan == null ? BigDecimal.ZERO : estimatedCostYuan;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String provider() {
        return provider;
    }

    public String businessType() {
        return businessType;
    }

    public String endpoint() {
        return endpoint;
    }

    public boolean success() {
        return success;
    }

    public long elapsedMs() {
        return elapsedMs;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public String modelNames() {
        return modelNames;
    }

    public int upstreamCallCount() {
        return upstreamCallCount;
    }

    public int retryCount() {
        return retryCount;
    }

    public long promptTokens() {
        return promptTokens;
    }

    public long completionTokens() {
        return completionTokens;
    }

    public long totalTokens() {
        return totalTokens;
    }

    public BigDecimal estimatedCostYuan() {
        return estimatedCostYuan;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
