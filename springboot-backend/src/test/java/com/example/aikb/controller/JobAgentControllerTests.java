package com.example.aikb.controller;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.entity.JobAnalysisTask;
import com.example.aikb.repository.JobAnalysisTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 求职辅助 Agent Spring Boot 入口测试。
 *
 * 这里不调用真实 FastAPI，也不调用真实大模型；
 * 只验证 Controller -> Service -> FastApiRagClient 的 Spring Boot 链路是否正确。
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobAgentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    @Autowired
    private JobAnalysisTaskRepository jobAnalysisTaskRepository;

    @BeforeEach
    void setUp() {
        jobAnalysisTaskRepository.deleteAll();
    }

    @Test
    void analyzeShouldReturnJobAnalyzeResponse() throws Exception {
        when(fastApiRagClient.analyzeJob(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG"
        )).thenReturn(new FastApiJobAnalyzeResponse(
                95,
                List.of("Java", "Spring Boot", "FastAPI", "RAG"),
                List.of("Redis"),
                List.of("端到端项目经验完整"),
                List.of("缓存经验体现较少"),
                List.of("补充 Redis 使用场景"),
                List.of("Rerank 解决了什么问题？")
        ));

        mockMvc.perform(post("/api/job-agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、FastAPI、RAG"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.match_score").value(95))
                .andExpect(jsonPath("$.data.matched_skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.missing_skills[0]").value("Redis"))
                .andExpect(jsonPath("$.data.strengths[0]").value("端到端项目经验完整"))
                .andExpect(jsonPath("$.data.interview_questions[0]").value("Rerank 解决了什么问题？"));

        verify(fastApiRagClient).analyzeJob(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG"
        );

        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc("demo-user");
        assertThat(tasks).hasSize(1);

        JobAnalysisTask task = tasks.get(0);
        assertThat(task.userId()).isEqualTo("demo-user");
        assertThat(task.resumeText()).isEqualTo("Spring Boot FastAPI RAG 项目");
        assertThat(task.jobDescription()).isEqualTo("需要 Java、Spring Boot、FastAPI、RAG");
        assertThat(task.matchScore()).isEqualTo(95);
        assertThat(task.resultJson()).contains("Spring Boot");
    }

    @Test
    void analyzeShouldReturnBadRequestWhenResumeTextIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "",
                                  "jobDescription": "需要 Java、Spring Boot、FastAPI、RAG"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("resumeText: 简历内容不能为空"));

        verify(fastApiRagClient, never()).analyzeJob(
                "",
                "需要 Java、Spring Boot、FastAPI、RAG"
        );
    }

    @Test
    void listTasksShouldReturnUserJobAnalysisHistory() throws Exception {
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                UUID.randomUUID(),
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG",
                95,
                "{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/tasks")
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data[0].userId").value("demo-user"))
                .andExpect(jsonPath("$.data[0].resumeText").value("Spring Boot FastAPI RAG 项目"))
                .andExpect(jsonPath("$.data[0].jobDescription").value("需要 Java、Spring Boot、FastAPI、RAG"))
                .andExpect(jsonPath("$.data[0].matchScore").value(95))
                .andExpect(jsonPath("$.data[0].resultJson").value("{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}"));
    }
}
