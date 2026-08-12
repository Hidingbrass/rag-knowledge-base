package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.enums.MessageRole;
import com.example.aikb.enums.ToolActionStatus;
import com.example.aikb.repository.ChatMessageRepository;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import com.example.aikb.tool.AuthorizedToolRegistry;
import com.example.aikb.tool.ToolExecutionResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class ToolActionServiceTests {
    @Autowired private KnowledgeBaseService knowledgeBaseService;
    @Autowired private ChatService chatService;
    @Autowired private ToolActionService toolActionService;
    @Autowired private KnowledgeDocumentRepository documentRepository;
    @Autowired private ChatMessageRepository messageRepository;

    @MockBean private FastApiRagClient fastApiRagClient;
    @MockBean private AuthorizedToolRegistry toolRegistry;
    @MockBean private AiRateLimitService aiRateLimitService;

    @Test
    void confirmationShouldExecuteBoundWriteToolOnceAndUpdateAuditMessage() {
        Fixture fixture = stageAction();
        when(toolRegistry.execute(anyString(), any(), any(), any()))
                .thenReturn(new ToolExecutionResult("已删除文档“安全测试.md”及其向量数据。"));

        var first = toolActionService.confirm(
                fixture.actionId(), "user-1", "dev", ToolActionService.CONFIRMATION_TEXT
        );
        var replay = toolActionService.confirm(
                fixture.actionId(), "user-1", "dev", ToolActionService.CONFIRMATION_TEXT
        );

        assertThat(first.status()).isEqualTo(ToolActionStatus.EXECUTED);
        assertThat(replay.status()).isEqualTo(ToolActionStatus.EXECUTED);
        ChatMessage updated = messageRepository.findById(fixture.messageId()).orElseThrow();
        assertThat(updated.content()).contains("已删除文档");
        assertThat(updated.routingDecisionJson()).contains("WRITE_TOOL_EXECUTED", "EXECUTED");
        verify(toolRegistry, times(1)).execute(anyString(), any(), any(), any());
    }

    @Test
    void wrongConfirmationOrDifferentUserMustNotExecute() {
        Fixture fixture = stageAction();

        assertThatThrownBy(() -> toolActionService.confirm(
                fixture.actionId(), "user-1", "dev", "DELETE"
        )).hasMessageContaining("确认文本不正确");
        assertThatThrownBy(() -> toolActionService.confirm(
                fixture.actionId(), "user-2", "dev", ToolActionService.CONFIRMATION_TEXT
        )).hasMessageContaining("无权确认");

        verify(toolRegistry, never()).execute(anyString(), any(), any(), any());
    }

    private Fixture stageAction() {
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "工具确认测试库", "test", "user-1", "dev"
        ));
        ChatSession session = chatService.createSession(new CreateChatSessionRequest(
                knowledgeBase.id(), "user-1", "dev", "工具确认测试"
        ));
        Instant now = Instant.now();
        documentRepository.save(new KnowledgeDocument(
                UUID.randomUUID(), knowledgeBase.id(), "tool-doc", "安全测试.md", "hash",
                DocumentStatus.AVAILABLE, 1, null, now, now
        ));
        UUID messageId = UUID.randomUUID();
        var staged = toolActionService.stageDocumentDeletion(
                session, "user-1", "dev", "tool-doc", messageId
        );
        messageRepository.save(new ChatMessage(
                messageId, session.id(), MessageRole.ASSISTANT, staged.prompt(), null,
                "tool_confirmation_required", null,
                "{\"route\":\"WRITE_TOOL_CONFIRMATION\",\"tool_action\":{\"id\":\""
                        + staged.action().id() + "\",\"status\":\"PENDING\"}}",
                now
        ));
        return new Fixture(staged.action().id(), messageId);
    }

    private record Fixture(UUID actionId, UUID messageId) {}
}
