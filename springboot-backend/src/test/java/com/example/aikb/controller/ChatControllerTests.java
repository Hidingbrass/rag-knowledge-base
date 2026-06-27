package com.example.aikb.controller;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import com.example.aikb.service.ChatService;
import com.example.aikb.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ChatController HTTP 层权限测试。
 *
 * 这里重点验证：只要请求通过 sessionId 访问聊天资源，
 * Controller -> Service -> GlobalExceptionHandler 这条链路最终会返回 HTTP 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    /**
     * 创建一个属于 user-1 / dev 的会话。
     *
     * 后续测试会让 user-3 / qa 访问这个会话，
     * 用来证明跨部门且非 owner 的用户会被拒绝。
     */
    private ChatSession createDevSession() {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Research Knowledge Base",
                "Used by chat controller permission tests",
                "user-1",
                "dev"
        ));

        return chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "dev",
                "RAG test session"
        ));
    }

    private void saveAvailableDocument(UUID knowledgeBaseId, String fastApiDocumentId) {
        Instant now = Instant.now();
        documentRepository.save(new KnowledgeDocument(
                UUID.randomUUID(),
                knowledgeBaseId,
                fastApiDocumentId,
                fastApiDocumentId + ".pdf",
                fastApiDocumentId + "-hash",
                DocumentStatus.AVAILABLE,
                1,
                null,
                now,
                now
        ));
    }

    @Test
    void createSessionShouldReturnForbiddenWhenUserHasNoAccessToKnowledgeBase() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Research Knowledge Base",
                "Used by chat controller permission tests",
                "user-1",
                "dev"
        ));

        mockMvc.perform(post("/api/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": "%s",
                                  "userId": "user-3",
                                  "department": "qa",
                                  "title": "Unauthorized session"
                                }
                                """.formatted(knowledgeBase.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问知识库: " + knowledgeBase.id()));
    }

    @Test
    void listMessagesShouldReturnForbiddenWhenUserHasNoAccess() throws Exception {
        ChatSession session = createDevSession();

        mockMvc.perform(get("/api/chat/sessions/{sessionId}/messages", session.id())
                        .param("userId", "user-3")
                        .param("department", "qa"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问知识库: " + session.knowledgeBaseId()));
    }

    @Test
    void askShouldReturnForbiddenBeforeCallingFastApiWhenUserHasNoAccess() throws Exception {
        ChatSession session = createDevSession();

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/ask", session.id())
                        .param("userId", "user-3")
                        .param("department", "qa")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is RAG?",
                                  "documentId": "fastapi-doc-1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问知识库: " + session.knowledgeBaseId()));

        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }

    @Test
    void askShouldReturnBadRequestWhenDocumentIdIsBlank() throws Exception {
        ChatSession session = createDevSession();

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/ask", session.id())
                        .param("userId", "user-1")
                        .param("department", "dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is RAG?",
                                  "documentId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("documentId: documentId 不能为空"));

        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }

    @Test
    void askShouldReturnForbiddenWhenDocumentBelongsToAnotherKnowledgeBase() throws Exception {
        ChatSession session = createDevSession();
        KnowledgeBase otherKnowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Finance Knowledge Base",
                "Used by document ownership tests",
                "finance-owner",
                "finance"
        ));
        saveAvailableDocument(otherKnowledgeBase.id(), "finance-doc-1");

        mockMvc.perform(post("/api/chat/sessions/{sessionId}/ask", session.id())
                        .param("userId", "user-1")
                        .param("department", "dev")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is the finance budget?",
                                  "documentId": "finance-doc-1"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权使用该文档: finance-doc-1"));

        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }
}
