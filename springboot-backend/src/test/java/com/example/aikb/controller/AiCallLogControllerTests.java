package com.example.aikb.controller;

import com.example.aikb.entity.AiCallLog;
import com.example.aikb.repository.AiCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiCallLogControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiCallLogRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void listRecentLogsShouldReturnLatestAiCallLogs() throws Exception {
        repository.save(new AiCallLog(
                UUID.randomUUID(),
                "FASTAPI",
                "JOB_ANALYZE",
                "/job/analyze",
                true,
                88,
                null,
                Instant.parse("2026-07-05T01:00:00Z")
        ));

        mockMvc.perform(get("/api/ai-call-logs/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].provider").value("FASTAPI"))
                .andExpect(jsonPath("$.data[0].businessType").value("JOB_ANALYZE"))
                .andExpect(jsonPath("$.data[0].endpoint").value("/job/analyze"))
                .andExpect(jsonPath("$.data[0].success").value(true))
                .andExpect(jsonPath("$.data[0].elapsedMs").value(88));
    }
}
