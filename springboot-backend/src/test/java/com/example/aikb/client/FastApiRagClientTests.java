package com.example.aikb.client;

import com.example.aikb.config.FastApiProperties;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.service.AiCallLogService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.DELETE;

class FastApiRagClientTests {

    @Test
    void askWithRerankShouldSendHybridSparseContract() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/rag/chat/rerank"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "question": "什么是 RAG？",
                          "candidate_k": 6,
                          "rerank_top_k": 3,
                          "rerank_min_score": 0.75,
                          "retrieval_mode": "hybrid",
                          "sparse_limit": 6,
                          "document_id": "doc-1"
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "question": "什么是 RAG？",
                          "answer": "检索增强生成 [1]。",
                          "sources": [],
                          "retrieval_mode": "rerank",
                          "candidate_retrieval_mode": "hybrid",
                          "rerank_error": null,
                          "rerank_elapsed_seconds": 0.1
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = testClient.client.askWithRerank("什么是 RAG？", "doc-1");

        assertThat(response.candidateRetrievalMode()).isEqualTo("hybrid");
        testClient.server.verify();
    }

    @Test
    void streamAskWithRerankShouldConsumeNdjsonEventsInOrder() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/rag/chat/rerank/stream"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "question": "什么是 RAG？",
                          "candidate_k": 6,
                          "rerank_top_k": 3,
                          "rerank_min_score": 0.75,
                          "retrieval_mode": "hybrid",
                          "sparse_limit": 6,
                          "document_id": "doc-1"
                        }
                        """))
                .andRespond(withSuccess("""
                        {"type":"status","stage":"retrieving","message":"正在检索"}
                        {"type":"delta","content":"检索增强"}
                        {"type":"delta","content":"生成"}
                        {"type":"done","retrieval_mode":"rerank"}
                        """, MediaType.parseMediaType("application/x-ndjson")));

        List<JsonNode> events = new ArrayList<>();
        testClient.client.streamAskWithRerank("什么是 RAG？", "doc-1", events::add);

        assertThat(events).extracting(event -> event.path("type").asText())
                .containsExactly("status", "delta", "delta", "done");
        assertThat(events.get(1).path("content").asText()).isEqualTo("检索增强");
        verify(testClient.logService).recordFastApiCall(
                eq("RAG_RERANK_CHAT_STREAM"),
                eq("/rag/chat/rerank/stream"),
                eq(true),
                anyLong(),
                eq(null)
        );
        testClient.server.verify();
    }

    @Test
    void classifyIntentShouldSendQuestionAndReadAuditFields() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/intent/classify"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"question":"1+1 等于多少？"}
                        """))
                .andRespond(withSuccess("""
                        {
                          "intent":"OPEN_DOMAIN_CHAT",
                          "confidence":0.98,
                          "enterprise_knowledge":false,
                          "reason_code":"open_domain",
                          "decision_source":"classifier",
                          "knowledge_scope":"PUBLIC",
                          "operation":"ANSWER",
                          "freshness":"STATIC",
                          "tool_name":null,
                          "missing_fields":[],
                          "requires_confirmation":false,
                          "model_usage":{
                            "models":["qwen-flash"],
                            "upstream_call_count":1,
                            "retry_count":0,
                            "prompt_tokens":20,
                            "completion_tokens":10,
                            "total_tokens":30,
                            "estimated_cost_yuan":0
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = testClient.client.classifyIntent("1+1 等于多少？");

        assertThat(response.intent()).isEqualTo("OPEN_DOMAIN_CHAT");
        assertThat(response.enterpriseKnowledge()).isFalse();
        assertThat(response.knowledgeScope()).isEqualTo("PUBLIC");
        assertThat(response.operation()).isEqualTo("ANSWER");
        assertThat(response.freshness()).isEqualTo("STATIC");
        verify(testClient.logService).recordFastApiCall(
                eq("INTENT_CLASSIFY"),
                eq("/intent/classify"),
                eq(true),
                anyLong(),
                eq(null),
                eq(response.modelUsage())
        );
        testClient.server.verify();
    }

    @Test
    void nonRagChatShouldSendControlledMode() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/chat"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {"question":"帮我查天气","mode":"tool_call"}
                        """))
                .andRespond(withSuccess("""
                        {"answer":"请告诉我城市。"}
                        """, MediaType.APPLICATION_JSON));

        var response = testClient.client.chatWithoutKnowledgeBase("帮我查天气", "tool_call");

        assertThat(response.answer()).isEqualTo("请告诉我城市。");
        verify(testClient.logService).recordFastApiCall(
                eq("NON_RAG_CHAT"),
                eq("/chat"),
                eq(true),
                anyLong(),
                eq(null)
        );
        testClient.server.verify();
    }

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
    void deleteDocumentShouldCallInternalVectorCleanupEndpoint() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/documents/vector-doc-1"))
                .andExpect(method(DELETE))
                .andRespond(withSuccess());

        testClient.client.deleteDocument("vector-doc-1");

        verify(testClient.logService).recordFastApiCall(
                eq("DOCUMENT_DELETE"),
                eq("/documents/{documentId}"),
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

    @Test
    void analyzeJobShouldExposeSafeFastApiErrorMessage() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/job/analyze"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error_code": "UPSTREAM_SERVICE_ERROR",
                                  "message": "Embedding 服务调用失败，请稍后重试"
                                }
                                """));

        assertThatThrownBy(() -> testClient.client.analyzeJob("Spring Boot RAG 项目", "需要 Java"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调用 FastAPI 求职分析服务失败")
                .hasMessageContaining("Embedding 服务调用失败，请稍后重试");

        testClient.server.verify();
    }

    @Test
    void generateJobDeliveryPackageShouldRecordSuccessfulFastApiCall() {
        TestClient testClient = newTestClient();
        testClient.server.expect(requestTo("http://fastapi.test/job/delivery-package"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "target_position": "Java 后端开发工程师",
                          "self_introduction": "我主要做 Java 后端和 AI 应用。",
                          "project_pitch": "我重点介绍企业知识库 RAG 项目。",
                          "architecture_talking_points": ["Spring Boot 负责业务层"],
                          "risk_response": ["Redis 可以结合限流说明"],
                          "closing_statement": "希望继续做 AI 应用落地。",
                          "rehearsal_checklist": ["练熟 RAG 链路"]
                        }
                        """, MediaType.APPLICATION_JSON));

        var response = testClient.client.generateJobDeliveryPackage("Spring Boot RAG 项目", "需要 Java");

        assertThat(response.targetPosition()).isEqualTo("Java 后端开发工程师");
        assertThat(response.architectureTalkingPoints()).contains("Spring Boot 负责业务层");
        verify(testClient.logService).recordFastApiCall(
                eq("JOB_DELIVERY_PACKAGE"),
                eq("/job/delivery-package"),
                eq(true),
                anyLong(),
                eq(null)
        );
        testClient.server.verify();
    }

    private TestClient newTestClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://fastapi.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiCallLogService logService = mock(AiCallLogService.class);
        FastApiRagClient client = new FastApiRagClient(
                builder.build(),
                new FastApiProperties("http://fastapi.test", "", 3, 60, 6, 3, 0.75, "hybrid", 6),
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
