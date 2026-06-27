package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.dto.fastapi.FastApiSource;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.enums.MessageRole;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.ChatMessageRepository;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatService 集成测试。
 *
 * 这里验证聊天会话和聊天消息会通过 JPA 保存到数据库。
 * FastAPI RAG 调用使用 mock，重点测试 Spring Boot 自己的业务编排。
 */
@SpringBootTest
@Transactional
class ChatServiceTests {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private KnowledgeDocumentRepository documentRepository;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    /**
     * 创建一个测试用知识库。
     *
     * ownerId=user-1，department=dev：
     * - user-1 是知识库所有者，可以访问；
     * - 其他 dev 部门用户也可以访问；
     * - 非 dev 部门且不是 owner 的用户应该被拒绝。
     */
    private KnowledgeBase createDevKnowledgeBase() {
        return knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Dev Knowledge Base",
                "Used by chat permission tests",
                "user-1",
                "dev"
        ));
    }

    /**
     * 创建一个 user-1 在 dev 知识库下的会话。
     *
     * 后续权限测试会用其他用户去访问这个会话，
     * 从而验证 ChatService 是否会根据会话所属知识库做权限判断。
     */
    private ChatSession createDevSession() {
        KnowledgeBase knowledgeBase = createDevKnowledgeBase();
        return chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "dev",
                "RAG test session"
        ));
    }

    private KnowledgeDocument saveDocument(UUID knowledgeBaseId, String fastApiDocumentId, DocumentStatus status) {
        Instant now = Instant.now();
        return documentRepository.save(new KnowledgeDocument(
                UUID.randomUUID(),
                knowledgeBaseId,
                fastApiDocumentId,
                fastApiDocumentId + ".pdf",
                fastApiDocumentId + "-hash",
                status,
                1,
                null,
                now,
                now
        ));
    }

    @Test
    void askShouldSaveUserAndAssistantMessages() {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "测试知识库",
                "用于测试聊天记录保存",
                "user-1",
                "研发部"
        ));

        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "研发部",
                "RAG 测试会话"
        ));
        saveDocument(knowledgeBase.id(), "fastapi-doc-1", DocumentStatus.AVAILABLE);

        FastApiRagResponse fastApiResponse = new FastApiRagResponse(
                "RAG 的英文全称是什么？",
                "RAG 的英文全称是 Retrieval-Augmented Generation [1]。",
                List.of(new FastApiSource(
                        "fastapi-doc-1",
                        "demo.pdf",
                        1,
                        0,
                        "RAG 的英文全称是 Retrieval-Augmented Generation。",
                        0.83,
                        0.95
                )),
                "rerank",
                null,
                1.23
        );

        when(fastApiRagClient.askWithRerank(
                "RAG 的英文全称是什么？",
                "fastapi-doc-1"
        )).thenReturn(fastApiResponse);

        FastApiRagResponse response = chatService.ask(
                session.id(),
                "user-1",
                "研发部",
                "RAG 的英文全称是什么？",
                "fastapi-doc-1"
        );

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id());

        assertThat(response.answer()).contains("Retrieval-Augmented Generation");
        assertThat(messages).hasSize(2);

        ChatMessage userMessage = messages.get(0);
        assertThat(userMessage.role()).isEqualTo(MessageRole.USER);
        assertThat(userMessage.content()).isEqualTo("RAG 的英文全称是什么？");
        assertThat(userMessage.sourcesJson()).isNull();

        ChatMessage assistantMessage = messages.get(1);
        assertThat(assistantMessage.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistantMessage.content()).contains("Retrieval-Augmented Generation");
        assertThat(assistantMessage.sourcesJson()).contains("demo.pdf");
        assertThat(assistantMessage.retrievalMode()).isEqualTo("rerank");
        assertThat(assistantMessage.rerankElapsedSeconds()).isEqualTo(1.23);
    }

    @Test
    void listMessagesShouldRejectUnauthorizedUser() {
        ChatSession session = createDevSession();

        // user-2 既不是知识库 owner，也不属于 dev 部门，所以不能读取这个会话的消息。
        assertThatThrownBy(() -> chatService.listMessages(session.id(), "user-2", "qa"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void askShouldRejectUnauthorizedUserBeforeSavingMessageOrCallingFastApi() {
        ChatSession session = createDevSession();

        // 权限检查必须发生在保存 USER 消息和调用 FastAPI 之前。
        // 否则无权限用户虽然拿不到答案，但数据库里可能已经留下了非法聊天记录。
        assertThatThrownBy(() -> chatService.ask(
                session.id(),
                "user-2",
                "qa",
                "What is RAG?",
                "fastapi-doc-1"
        )).isInstanceOf(BusinessException.class);

        assertThat(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id())).isEmpty();
        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }

    @Test
    void askShouldRejectDocumentFromAnotherKnowledgeBaseBeforeSavingMessageOrCallingFastApi() {
        ChatSession session = createDevSession();
        KnowledgeBase otherKnowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Finance Knowledge Base",
                "Used by document ownership tests",
                "finance-owner",
                "finance"
        ));
        saveDocument(otherKnowledgeBase.id(), "finance-doc-1", DocumentStatus.AVAILABLE);

        assertThatThrownBy(() -> chatService.ask(
                session.id(),
                "user-1",
                "dev",
                "What is the finance budget?",
                "finance-doc-1"
        )).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权使用该文档");

        assertThat(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id())).isEmpty();
        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }
}
