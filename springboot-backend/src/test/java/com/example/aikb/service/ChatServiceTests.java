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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

    @MockBean
    private AiRateLimitService aiRateLimitService;

    /**
     * 创建一个测试用知识库。
     *
     * ownerId=user-1，department=dev：
     * - user-1 是知识库所有者，可以访问；
     * - 其他用户即使学习方向同为 dev，也不能访问。
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
    void askShouldReplyToGreetingWithoutCallingAiServices() {
        KnowledgeBase knowledgeBase = createDevKnowledgeBase();
        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "dev",
                "小聊同步会话"
        ));
        saveDocument(knowledgeBase.id(), "small-talk-doc", DocumentStatus.AVAILABLE);

        FastApiRagResponse response = chatService.ask(
                session.id(),
                "user-1",
                "dev",
                "你好！",
                "small-talk-doc"
        );

        assertThat(response.answer()).contains("知途 AI");
        assertThat(response.sources()).isEmpty();
        assertThat(response.retrievalMode()).isEqualTo("small_talk");

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).content()).isEqualTo("你好！");
        assertThat(messages.get(1).content()).isEqualTo(response.answer());
        assertThat(messages.get(1).sourcesJson()).isNull();
        assertThat(messages.get(1).retrievalMode()).isEqualTo("small_talk");
        assertThat(messages.get(1).rerankElapsedSeconds()).isNull();

        verify(aiRateLimitService, never()).checkAiCallAllowed(anyString(), anyString());
        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }

    @Test
    void mixedGreetingAndKnowledgeQuestionShouldStillUseRag() {
        KnowledgeBase knowledgeBase = createDevKnowledgeBase();
        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "dev",
                "混合问题会话"
        ));
        saveDocument(knowledgeBase.id(), "mixed-question-doc", DocumentStatus.AVAILABLE);

        String question = "你好，请总结这份文档";
        FastApiRagResponse fastApiResponse = new FastApiRagResponse(
                question,
                "这份文档介绍了检索增强生成 [1]。",
                List.of(),
                "rerank",
                "hybrid",
                null,
                0.3
        );
        when(fastApiRagClient.askWithRerank(question, "mixed-question-doc"))
                .thenReturn(fastApiResponse);

        FastApiRagResponse response = chatService.ask(
                session.id(),
                "user-1",
                "dev",
                question,
                "mixed-question-doc"
        );

        assertThat(response).isSameAs(fastApiResponse);
        assertThat(response.retrievalMode()).isEqualTo("rerank");
        verify(aiRateLimitService).checkAiCallAllowed("user-1", "RAG_CHAT");
        verify(fastApiRagClient).askWithRerank(question, "mixed-question-doc");
    }

    @Test
    void streamAnswerShouldReplyToCapabilityQuestionWithoutCallingAiServices() {
        KnowledgeBase knowledgeBase = createDevKnowledgeBase();
        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "dev",
                "小聊流式会话"
        ));
        saveDocument(knowledgeBase.id(), "small-talk-stream-doc", DocumentStatus.AVAILABLE);

        ChatService.StreamingAsk prepared = chatService.prepareStreamingAsk(
                session.id(),
                "user-1",
                "dev",
                "你能做什么？",
                "small-talk-stream-doc"
        );

        assertThat(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id())).hasSize(1);

        List<JsonNode> browserEvents = new java.util.ArrayList<>();
        chatService.streamAnswer(prepared, browserEvents::add);

        assertThat(browserEvents).extracting(event -> event.path("type").asText())
                .containsExactly("delta", "done");
        assertThat(browserEvents.get(0).path("content").asText()).contains("资料");
        assertThat(browserEvents.get(1).path("message").path("retrievalMode").asText())
                .isEqualTo("small_talk");

        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(1).sourcesJson()).isNull();
        assertThat(messages.get(1).retrievalMode()).isEqualTo("small_talk");

        verify(aiRateLimitService, never()).checkAiCallAllowed(anyString(), anyString());
        verify(fastApiRagClient, never()).streamAskWithRerank(anyString(), anyString(), any());
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
                        null,
                        0.75,
                        0.95
                )),
                "rerank",
                "hybrid",
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
        verify(aiRateLimitService).checkAiCallAllowed("user-1", "RAG_CHAT");
    }

    @Test
    void streamAnswerShouldSaveUserImmediatelyAndAssistantAfterDoneEvent() throws Exception {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "流式问答知识库",
                "用于测试流式消息持久化",
                "user-1",
                "研发部"
        ));
        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "研发部",
                "流式 RAG 会话"
        ));
        saveDocument(knowledgeBase.id(), "stream-doc-1", DocumentStatus.AVAILABLE);

        ObjectMapper mapper = new ObjectMapper();
        doAnswer(invocation -> {
            Consumer<JsonNode> consumer = invocation.getArgument(2);
            consumer.accept(mapper.readTree("""
                    {"type":"status","stage":"retrieving","message":"正在检索"}
                    """));
            consumer.accept(mapper.readTree("""
                    {
                      "type":"sources",
                      "sources":[{"filename":"stream.md","page_number":1}],
                      "retrieval_mode":"rerank",
                      "rerank_elapsed_seconds":0.2
                    }
                    """));
            consumer.accept(mapper.readTree("""
                    {"type":"delta","content":"第一段"}
                    """));
            consumer.accept(mapper.readTree("""
                    {"type":"delta","content":"第二段"}
                    """));
            consumer.accept(mapper.readTree("""
                    {"type":"done","retrieval_mode":"rerank","rerank_elapsed_seconds":0.2}
                    """));
            return null;
        }).when(fastApiRagClient).streamAskWithRerank(
                anyString(),
                anyString(),
                any()
        );

        ChatService.StreamingAsk prepared = chatService.prepareStreamingAsk(
                session.id(),
                "user-1",
                "研发部",
                "请流式回答",
                "stream-doc-1"
        );

        List<ChatMessage> messagesAfterAccept =
                chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id());
        assertThat(messagesAfterAccept).hasSize(1);
        assertThat(messagesAfterAccept.get(0).role()).isEqualTo(MessageRole.USER);

        List<JsonNode> browserEvents = new java.util.ArrayList<>();
        chatService.streamAnswer(prepared, browserEvents::add);

        assertThat(browserEvents).extracting(event -> event.path("type").asText())
                .containsExactly("status", "sources", "delta", "delta", "done");
        assertThat(browserEvents.get(2).path("content").asText()).isEqualTo("第一段");

        List<ChatMessage> completedMessages =
                chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id());
        assertThat(completedMessages).hasSize(2);
        ChatMessage assistant = completedMessages.get(1);
        assertThat(assistant.role()).isEqualTo(MessageRole.ASSISTANT);
        assertThat(assistant.content()).isEqualTo("第一段第二段");
        assertThat(assistant.sourcesJson()).contains("stream.md");
        assertThat(assistant.retrievalMode()).isEqualTo("rerank");
        assertThat(assistant.rerankElapsedSeconds()).isEqualTo(0.2);
        assertThat(browserEvents.get(4).path("message").path("content").asText())
                .isEqualTo("第一段第二段");
    }

    @Test
    void listMessagesShouldRejectUnauthorizedUser() {
        ChatSession session = createDevSession();

        // user-2 与 owner 的学习方向相同，但个人知识库仍然只能由 owner 访问。
        assertThatThrownBy(() -> chatService.listMessages(session.id(), "user-2", "dev"))
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
                "dev",
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

    @Test
    void greetingShouldNotBypassDocumentOwnershipValidation() {
        ChatSession session = createDevSession();
        KnowledgeBase otherKnowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Other Knowledge Base",
                "Used by small-talk ownership tests",
                "other-owner",
                "other"
        ));
        saveDocument(otherKnowledgeBase.id(), "other-doc", DocumentStatus.AVAILABLE);

        assertThatThrownBy(() -> chatService.ask(
                session.id(),
                "user-1",
                "dev",
                "你好",
                "other-doc"
        )).isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("无权使用该文档");

        assertThat(chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.id())).isEmpty();
        verify(aiRateLimitService, never()).checkAiCallAllowed(anyString(), anyString());
        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }
}
