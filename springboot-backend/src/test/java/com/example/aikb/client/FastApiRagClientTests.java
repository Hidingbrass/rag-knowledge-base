package com.example.aikb.client;

import com.example.aikb.config.FastApiProperties;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.service.AiCallLogService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class FastApiRagClientTests {

    @Test
    void analyzeJobShouldRecordSuccessfulFastApiCall() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/job/analyze"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "match_score": 90,
                          "matched_skills": ["Java"],
                          "missing_skills": [],
                          "strengths": ["项目完整"],
                          "risks": [],
                          "suggestions": ["继续准备"],
                          "interview_questions": ["如何做权限校验？"]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = testClient.client.analyzeJob("Spring Boot RAG 项目", "需要 Java");

        assertThat(response.matchScore()).isEqualTo(90);
        verify(testClient.logService).recordFastApiCall(
                eq("JOB_ANALYZE"),
                eq("/job/analyze"),
                eq(true),
                anyLong(),
                eq(null)
        );
        testClient.server.verify();
    }

    @Test
    void analyzeJobShouldRecordFailedFastApiCall() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/job/analyze"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> testClient.client.analyzeJob("Spring Boot RAG 项目", "需要 Java"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调用 FastAPI 求职分析服务失败");

        verify(testClient.logService).recordFastApiCall(
                eq("JOB_ANALYZE"),
                eq("/job/analyze"),
                eq(false),
                anyLong(),
                contains("500")
        );
        testClient.server.verify();
    }

    private TestClient newTestClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://fastapi.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiCallLogService logService = mock(AiCallLogService.class);
        FastApiRagClient client = new FastApiRagClient(
                builder.build(),
                new FastApiProperties("http://fastapi.test", 3, 60, 6, 3, 0.75, "hybrid", 6),
                logService
        );
        return new TestClient(client, server, logService);
    }

    private record TestClient(
            FastApiRagClient client,
            MockRestServiceServer server,
            AiCallLogService logService
    ) {
    }
}
