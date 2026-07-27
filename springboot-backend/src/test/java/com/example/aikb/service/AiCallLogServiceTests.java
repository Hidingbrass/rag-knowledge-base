package com.example.aikb.service;

import com.example.aikb.dto.fastapi.FastApiModelUsage;
import com.example.aikb.repository.AiCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AiCallLogServiceTests {

    @Autowired
    private AiCallLogService service;

    @Autowired
    private AiCallLogRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void recordFastApiCallShouldSaveSuccessLog() {
        service.recordFastApiCall("JOB_ANALYZE", "/job/analyze", true, 123, null);

        var logs = repository.findTop20ByOrderByCreatedAtDesc();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).provider()).isEqualTo("FASTAPI");
        assertThat(logs.get(0).businessType()).isEqualTo("JOB_ANALYZE");
        assertThat(logs.get(0).endpoint()).isEqualTo("/job/analyze");
        assertThat(logs.get(0).success()).isTrue();
        assertThat(logs.get(0).elapsedMs()).isEqualTo(123);
        assertThat(logs.get(0).errorMessage()).isNull();
        assertThat(logs.get(0).createdAt()).isNotNull();
    }

    @Test
    void recordFastApiCallShouldTruncateLongErrorMessage() {
        service.recordFastApiCall("RAG_RERANK_CHAT", "/rag/chat/rerank", false, 456, "x".repeat(1200));

        var logs = repository.findTop20ByOrderByCreatedAtDesc();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).success()).isFalse();
        assertThat(logs.get(0).errorMessage()).hasSize(1000);
    }

    @Test
    void summarizeShouldAggregateSuccessLatencyTokensRetriesAndCost() {
        service.recordFastApiCall(
                "RAG_CHAT",
                "/rag/chat",
                true,
                100,
                null,
                new FastApiModelUsage(
                        List.of("qwen-plus"),
                        2,
                        1,
                        80,
                        20,
                        100,
                        new BigDecimal("0.012300")
                )
        );
        service.recordFastApiCall("RAG_CHAT", "/rag/chat", false, 900, "timeout");

        var summary = service.summarize(24);

        assertThat(summary.totalCalls()).isEqualTo(2);
        assertThat(summary.successfulCalls()).isEqualTo(1);
        assertThat(summary.failedCalls()).isEqualTo(1);
        assertThat(summary.successRate()).isEqualTo(0.5);
        assertThat(summary.averageElapsedMs()).isEqualTo(500);
        assertThat(summary.p95ElapsedMs()).isEqualTo(900);
        assertThat(summary.totalTokens()).isEqualTo(100);
        assertThat(summary.totalRetries()).isEqualTo(1);
        assertThat(summary.estimatedCostYuan()).isEqualByComparingTo("0.012300");
        assertThat(summary.operations()).singleElement()
                .satisfies(operation -> {
                    assertThat(operation.businessType()).isEqualTo("RAG_CHAT");
                    assertThat(operation.successRate()).isEqualTo(0.5);
                });
    }
}
