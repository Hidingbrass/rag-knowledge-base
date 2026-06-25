package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.entity.ChatMessage;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.enums.MessageRole;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.repository.InMemoryChatMessageRepository;
import com.example.aikb.repository.InMemoryChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 聊天业务服务。
 * <p>
 * 这个类负责“聊天功能的业务流程”，不直接处理 HTTP 请求。
 * <p>
 * 当前已经包含：
 * - 创建聊天会话；
 * - 查询会话是否存在；
 * - 查询某个会话下的历史消息。
 * <p>
 * 下一步会继续加入：
 * - 保存用户问题；
 * - 调用 FastAPI RAG；
 * - 保存 AI 回答和引用来源。
 */
@Service
public class ChatService {

    /**
     * 用来检查 knowledgeBaseId 是否存在。
     * <p>
     * 创建会话前必须确认知识库存在，否则会出现“会话绑定了一个不存在知识库”的脏数据。
     */
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 聊天会话仓库。
     * <p>
     * 当前是内存版，后续接 MySQL 时会替换成 JPA Repository。
     */
    private final InMemoryChatSessionRepository chatSessionRepository;

    /**
     * 聊天消息仓库。
     * <p>
     * 用来保存和查询 USER / ASSISTANT 消息。
     */
    private final InMemoryChatMessageRepository chatMessageRepository;

    /**
     * FastAPI RAG 客户端。
     * <p>
     * 当前前三个方法还没有用到它；下一步 ask() 方法会用它调用 FastAPI /rag/chat/rerank。
     */
    private final FastApiRagClient fastApiRagClient;

    /**
     * JSON 工具。
     * <p>
     * 下一步会用它把 FastAPI 返回的 sources 列表转换成 sourcesJson 字符串保存。
     */
    private final ObjectMapper objectMapper;

    public ChatService(
            KnowledgeBaseService knowledgeBaseService,
            InMemoryChatSessionRepository chatSessionRepository,
            InMemoryChatMessageRepository chatMessageRepository,
            FastApiRagClient fastApiRagClient,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.fastApiRagClient = fastApiRagClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建聊天会话。
     * <p>
     * 流程：
     * 1. 先检查知识库是否存在；
     * 2. 生成会话 ID；
     * 3. 保存会话；
     * 4. 返回保存后的会话对象。
     */
    public ChatSession createSession(CreateChatSessionRequest request) {
        knowledgeBaseService.getRequired(request.knowledgeBaseId());

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
     * 查询某个会话下的历史消息。
     * <p>
     * 先调用 getRequiredSession(sessionId)，是为了避免查询一个不存在会话的消息。
     */
    public List<ChatMessage> listMessages(UUID sessionId) {
        getRequiredSession(sessionId);
        return chatMessageRepository.findBySessionId(sessionId);
    }

    /**
     * 在指定会话里发起一次 RAG 问答，并保存聊天记录。
     *
     * 流程：
     * 1. 确认会话存在；
     * 2. 先保存 USER 消息，记录用户问了什么；
     * 3. 调用 FastAPI RAG 服务生成回答；
     * 4. 把 FastAPI 返回的 sources 转成 JSON 字符串；
     * 5. 保存 ASSISTANT 消息，记录 AI 回答、引用来源、检索模式和 rerank 耗时；
     * 6. 返回 FastAPI 原始响应，方便前端立刻展示答案和 sources。
     *
     * documentId 是 FastAPI/Qdrant 侧的 document_id，
     * 用来限制本次 RAG 只检索指定文档。
     */
    public FastApiRagResponse ask(UUID sessionId, String question, String documentId) {
        ChatSession session = getRequiredSession(sessionId);

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
     *
     * sources 是一个结构化数组，里面有 filename、page_number、vector_score、rerank_score 等字段。
     * 当前 MVP 先整体保存成 JSON 字符串，后续接 MySQL 时可以直接放到 JSON/TEXT 字段里。
     */
    private String toSourcesJson(FastApiRagResponse response) {
        try {
            return objectMapper.writeValueAsString(response.sources());
        } catch (Exception exception) {
            throw new BusinessException("保存引用来源失败", exception);
        }
    }
}
