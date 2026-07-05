package com.example.aikb.service;

import com.example.aikb.repository.AiCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
