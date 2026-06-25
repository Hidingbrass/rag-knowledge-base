package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.dto.fastapi.FastApiSource;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.enums.MessageRole;
import com.example.aikb.repository.InMemoryChatMessageRepository;
import com.example.aikb.repository.InMemoryChatSessionRepository;
import com.example.aikb.repository.InMemoryKnowledgeBaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ChatService 单元测试。
 *
 * 这里不调用真实 FastAPI、Qdrant 或模型服务。
 * 通过 mock FastApiRagClient，只验证 Spring Boot 自己的聊天业务编排：
 * - 能创建会话；
 * - 会话内提问时保存 USER 消息；
 * - FastAPI 返回后保存 ASSISTANT 消息；
 * - AI 消息中能保存 sourcesJson、retrievalMode、rerankElapsedSeconds。
 */
class ChatServiceTests {

    @Test
    void askShouldSaveUserAndAssistantMessages() {
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(
                new InMemoryKnowledgeBaseRepository()
        );
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "测试知识库",
                "用于测试聊天记录保存",
                "user-1",
                "研发部"
        ));

        InMemoryChatMessageRepository messageRepository = new InMemoryChatMessageRepository();
        FastApiRagClient fastApiRagClient = mock(FastApiRagClient.class);
        ChatService chatService = new ChatService(
                knowledgeBaseService,
                new InMemoryChatSessionRepository(),
                messageRepository,
                fastApiRagClient,
                new ObjectMapper()
        );

        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(),
                "user-1",
                "RAG 测试会话"
        ));

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
                "RAG 的英文全称是什么？",
                "fastapi-doc-1"
        );

        List<ChatMessage> messages = messageRepository.findBySessionId(session.id());

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
}
