package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.enums.MessageRole;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.ChatMessageRepository;
import com.example.aikb.repository.ChatSessionRepository;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 聊天业务服务。
 * <p>
 * 这个类负责“聊天功能的业务流程”，Controller 只负责接收 HTTP 参数。
 * 核心职责：
 * - 创建聊天会话；
 * - 查询会话和历史消息；
 * - 校验用户是否能访问会话所属知识库；
 * - 校验 documentId 是否属于当前会话的知识库；
 * - 保存 USER / ASSISTANT 消息；
 * - 调用 FastAPI RAG 并保存引用来源。
 */
@Service
public class ChatService {

    /**
     * 知识库业务服务。
     * <p>
     * 创建会话、读取消息和提问时，都需要通过它校验当前用户是否能访问目标知识库。
     */
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 聊天会话仓库。
     * <p>
     * 当前已经是 JPA Repository，数据会保存到 MySQL；测试环境则保存到 H2 内存数据库。
     */
    private final ChatSessionRepository chatSessionRepository;

    /**
     * 聊天消息仓库。
     * <p>
     * 用来保存和查询 USER / ASSISTANT 消息。
     */
    private final ChatMessageRepository chatMessageRepository;

    /**
     * 文档业务记录仓库。
     * <p>
     * RAG 真正检索的是 FastAPI/Qdrant 的 document_id，
     * 但这个 document_id 属于哪个知识库，只有 Spring Boot 的 MySQL 业务表知道。
     */
    private final KnowledgeDocumentRepository documentRepository;

    /**
     * FastAPI RAG 客户端。
     *
     * 只在 ask() 的权限和文档归属校验全部通过后调用，避免无权限请求浪费模型调用。
     */
    private final FastApiRagClient fastApiRagClient;

    /**
     * JSON 工具。
     *
     * 用于把 FastAPI 返回的 sources 列表转换成 sourcesJson 字符串保存到聊天消息表。
     */
    private final ObjectMapper objectMapper;

    public ChatService(
            KnowledgeBaseService knowledgeBaseService,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            KnowledgeDocumentRepository documentRepository,
            FastApiRagClient fastApiRagClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.fastApiRagClient = fastApiRagClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建聊天会话。
     * <p>
     * 流程：
     * 1. 校验当前用户是否能访问目标知识库；
     * 2. 生成会话 ID；
     * 3. 保存会话；
     * 4. 返回保存后的会话对象。
     */
    public ChatSession createSession(CreateChatSessionRequest request) {
        knowledgeBaseService.getRequiredWithAccess(request.knowledgeBaseId(), request.userId(), request.department());

        ChatSession chatSession = new ChatSession(
                UUID.randomUUID(),
                request.knowledgeBaseId(),
                request.userId(),
                request.title(),
                Instant.now()
        );

        return chatSessionRepository.save(chatSession);
    }

    /**
     * 根据 sessionId 查询会话。
     * <p>
     * 方法名里的 Required 表示：调用方要求这个会话必须存在。
     * 如果不存在，就抛 BusinessException，让全局异常处理器返回统一错误响应。
     */
    public ChatSession getRequiredSession(UUID sessionId) {
        return chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在: " + sessionId));
    }

    /**
     * 查询会话，并校验当前用户能访问会话所属知识库。
     *
     * 很多接口的路径里只有 sessionId，没有 knowledgeBaseId。
     * 所以要先从会话记录中拿到 knowledgeBaseId，再复用 KnowledgeBaseService 的权限规则。
     */
    public ChatSession getRequiredSessionWithAccess(
            UUID sessionId,
            String userId,
            String department
    ) {
        ChatSession session = getRequiredSession(sessionId);
        knowledgeBaseService.getRequiredWithAccess(session.knowledgeBaseId(), userId, department);
        return session;
    }

    /**
     * 查询某个用户创建过的会话列表。
     * <p>
     * 前端刷新后，浏览器内存里的 sessionId 会丢失。
     * 这个方法可以从 MySQL 重新加载历史会话，让页面恢复到可继续操作的状态。
     */
    public List<ChatSession> listSessionsByUserId(String userId) {
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询某个会话下的历史消息。
     * <p>
     * 读取消息前必须先校验当前用户能访问这个会话所属的知识库。
     * 否则只要猜到 sessionId，就可能读取别人的聊天记录。
     */
    public List<ChatMessage> listMessages(
            UUID sessionId,
            String userId,
            String department
    ) {
        getRequiredSessionWithAccess(sessionId, userId, department);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 校验本次提问使用的 documentId 是否属于当前会话的知识库。
     * <p>
     * 只校验“用户能访问 session”还不够，因为请求体里的 documentId 也可能被手动篡改。
     * 如果不做这一步，用户可能在一个有权限的会话里传入其他知识库的 documentId，
     * 从而让 FastAPI/Qdrant 检索到不该访问的文档。
     */
    private void validateDocumentBelongsToSessionKnowledgeBase(String documentId, UUID knowledgeBaseId) {
        if (documentId == null || documentId.isBlank()) {
            return;
        }

        KnowledgeDocument document = documentRepository.findFirstByFastApiDocumentId(documentId)
                .orElseThrow(() -> new BusinessException("文档不存在: " + documentId));

        if (!document.knowledgeBaseId().equals(knowledgeBaseId)) {
            throw new ForbiddenException("无权使用该文档: " + documentId);
        }

        if (document.status() != DocumentStatus.AVAILABLE) {
            throw new BusinessException("文档当前不可用: " + documentId);
        }
    }

    /**
     * 在指定会话里发起一次 RAG 问答，并保存聊天记录。
     * <p>
     * 流程：
     * 1. 校验用户能访问会话所属知识库；
     * 2. 校验 documentId 属于当前会话的知识库，并且文档状态为 AVAILABLE；
     * 3. 保存 USER 消息，记录用户问了什么；
     * 4. 调用 FastAPI RAG 服务生成回答；
     * 5. 把 FastAPI 返回的 sources 转成 JSON 字符串；
     * 6. 保存 ASSISTANT 消息，记录 AI 回答、引用来源、检索模式和 rerank 耗时；
     * 7. 返回 FastAPI 原始响应，方便前端立刻展示答案和 sources。
     * <p>
     * documentId 是 FastAPI/Qdrant 侧的 document_id，
     * 用来限制本次 RAG 只检索指定文档。
     */
    public FastApiRagResponse ask(UUID sessionId,
                                  String userId,
                                  String department,
                                  String question,
                                  String documentId) {
        ChatSession session = getRequiredSessionWithAccess(sessionId, userId, department);
        validateDocumentBelongsToSessionKnowledgeBase(documentId, session.knowledgeBaseId());

        ChatMessage userMessage = new ChatMessage(
                UUID.randomUUID(),
                session.id(),
                MessageRole.USER,
                question,
                null,
                null,
                null,
                Instant.now()
        );
        chatMessageRepository.save(userMessage);

        FastApiRagResponse response = fastApiRagClient.askWithRerank(question, documentId);
        String sourcesJson = toSourcesJson(response);

        ChatMessage assistantMessage = new ChatMessage(
                UUID.randomUUID(),
                session.id(),
                MessageRole.ASSISTANT,
                response.answer(),
                sourcesJson,
                response.retrievalMode(),
                response.rerankElapsedSeconds(),
                Instant.now()
        );
        chatMessageRepository.save(assistantMessage);

        return response;
    }

    /**
     * 把 FastAPI 返回的 sources 转成 JSON 字符串。
     * <p>
     * sources 是一个结构化数组，里面有 filename、page_number、vector_score、rerank_score 等字段。
     * 当前先整体保存成 JSON 字符串，对应 chat_message.sources_json 这类 TEXT 字段；
     * 这样实现简单，也方便前端直接展示完整引用来源。
     */
    private String toSourcesJson(FastApiRagResponse response) {
        try {
            return objectMapper.writeValueAsString(response.sources());
        } catch (Exception exception) {
            throw new BusinessException("保存引用来源失败", exception);
        }
    }
}
