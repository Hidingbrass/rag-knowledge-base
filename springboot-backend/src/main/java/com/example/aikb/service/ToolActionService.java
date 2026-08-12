package com.example.aikb.service;

import com.example.aikb.config.ToolExecutionProperties;
import com.example.aikb.dto.tool.ToolActionResponse;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.entity.ToolAction;
import com.example.aikb.enums.ToolActionStatus;
import com.example.aikb.enums.ToolOperation;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.ChatMessageRepository;
import com.example.aikb.repository.ChatSessionRepository;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import com.example.aikb.repository.ToolActionRepository;
import com.example.aikb.tool.AuthorizedToolRegistry;
import com.example.aikb.tool.DeleteKnowledgeDocumentTool;
import com.example.aikb.tool.ToolExecutionContext;
import com.example.aikb.tool.ToolExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.example.aikb.common.RequestIdentity.requireDepartment;
import static com.example.aikb.common.RequestIdentity.requireUserId;

/** 写工具的服务端参数绑定、短时确认、一次性执行和审计。 */
@Service
public class ToolActionService {
    public static final String CONFIRMATION_TEXT = "CONFIRM";

    private final ToolActionRepository actionRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuthorizedToolRegistry toolRegistry;
    private final ToolExecutionProperties properties;
    private final ObjectMapper objectMapper;

    public ToolActionService(
            ToolActionRepository actionRepository,
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeBaseService knowledgeBaseService,
            AuthorizedToolRegistry toolRegistry,
            ToolExecutionProperties properties,
            ObjectMapper objectMapper
    ) {
        this.actionRepository = actionRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.documentRepository = documentRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.toolRegistry = toolRegistry;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StagedToolAction stageDocumentDeletion(
            ChatSession session,
            String userId,
            String department,
            String fastApiDocumentId,
            UUID assistantMessageId
    ) {
        String requiredUserId = requireUserId(userId);
        String requiredDepartment = requireDepartment(department);
        knowledgeBaseService.getRequiredWithAccess(
                session.knowledgeBaseId(), requiredUserId, requiredDepartment
        );
        KnowledgeDocument document = documentRepository.findFirstByFastApiDocumentId(fastApiDocumentId)
                .orElseThrow(() -> new BusinessException("文档不存在: " + fastApiDocumentId));
        if (!document.knowledgeBaseId().equals(session.knowledgeBaseId())) {
            throw new ForbiddenException("无权删除该文档: " + fastApiDocumentId);
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.confirmationTtlSeconds());
        ObjectNode arguments = objectMapper.createObjectNode();
        arguments.put("knowledge_base_id", session.knowledgeBaseId().toString());
        arguments.put("document_id", fastApiDocumentId);
        arguments.put("filename", document.filename());
        ToolAction action = new ToolAction(
                UUID.randomUUID(),
                requiredUserId,
                requiredDepartment,
                session.id(),
                assistantMessageId,
                DeleteKnowledgeDocumentTool.NAME,
                writeJson(arguments),
                expiresAt,
                now
        );
        actionRepository.save(action);
        String prompt = "已准备删除当前文档“%s”。确认后会删除业务记录和向量数据，且不可恢复；本次确认将在 %d 分钟内失效。"
                .formatted(document.filename(), Math.max(1, properties.confirmationTtlSeconds() / 60));
        return new StagedToolAction(action, prompt);
    }

    @Transactional
    public ToolActionResponse confirm(
            UUID actionId,
            String userId,
            String department,
            String confirmation
    ) {
        String requiredUserId = requireUserId(userId);
        requireDepartment(department);
        ToolAction action = actionRepository.findByIdForUpdate(actionId)
                .orElseThrow(() -> new BusinessException("待确认工具操作不存在: " + actionId));
        if (!action.userId().equals(requiredUserId)) {
            throw new ForbiddenException("无权确认该工具操作");
        }
        if (action.status() == ToolActionStatus.EXECUTED
                || action.status() == ToolActionStatus.EXPIRED) {
            return ToolActionResponse.from(action);
        }
        if (!CONFIRMATION_TEXT.equals(confirmation)) {
            throw new BusinessException("确认文本不正确，未执行任何操作");
        }

        ChatSession session = sessionRepository.findById(action.sessionId())
                .orElseThrow(() -> new BusinessException("工具操作关联会话不存在"));
        knowledgeBaseService.getRequiredWithAccess(
                session.knowledgeBaseId(), requiredUserId, action.department()
        );
        Instant now = Instant.now();
        if (!now.isBefore(action.expiresAt())) {
            action.markExpired(now);
            updateAssistantMessage(action, action.resultSummary());
            return ToolActionResponse.from(action);
        }

        JsonNode arguments = readJson(action.argumentsJson());
        ToolExecutionResult result = toolRegistry.execute(
                action.toolName(),
                ToolOperation.WRITE,
                new ToolExecutionContext(requiredUserId, action.department(), action.sessionId()),
                arguments
        );
        action.markExecuted(result.answer(), now);
        updateAssistantMessage(action, result.answer());
        return ToolActionResponse.from(action);
    }

    private void updateAssistantMessage(ToolAction action, String content) {
        ChatMessage message = messageRepository.findById(action.assistantMessageId())
                .orElseThrow(() -> new BusinessException("工具操作关联消息不存在"));
        ObjectNode audit = objectMapper.createObjectNode();
        try {
            JsonNode existing = objectMapper.readTree(message.routingDecisionJson());
            if (existing instanceof ObjectNode objectNode) {
                audit = objectNode;
            }
        } catch (Exception ignored) {
            // 旧消息审计损坏时也不能绕过 action 表中的服务端参数与状态。
        }
        audit.put("route", action.status() == ToolActionStatus.EXECUTED
                ? "WRITE_TOOL_EXECUTED" : "WRITE_TOOL_EXPIRED");
        audit.put("requires_confirmation", false);
        ObjectNode actionAudit = audit.get("tool_action") instanceof ObjectNode existingActionAudit
                ? existingActionAudit
                : audit.putObject("tool_action");
        actionAudit.put("id", action.id().toString());
        actionAudit.put("tool_name", action.toolName());
        actionAudit.put("status", action.status().name());
        if (action.executedAt() != null) {
            actionAudit.put("executed_at", action.executedAt().toString());
        }
        message.replaceToolActionResult(content, writeJson(audit));
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("保存工具操作参数失败", exception);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new BusinessException("读取工具操作参数失败", exception);
        }
    }

    public record StagedToolAction(ToolAction action, String prompt) {
    }
}
