package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.chat.ChatMessageResponse;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.dto.fastapi.FastApiChatResponse;
import com.example.aikb.dto.fastapi.FastApiIntentClassificationResponse;
import com.example.aikb.config.IntentRoutingProperties;
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
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static com.example.aikb.common.RequestIdentity.requireDepartment;
import static com.example.aikb.common.RequestIdentity.requireUserId;

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
 * - 对有限小聊执行本地确定性回复；
 * - 调用 FastAPI RAG 并保存引用来源。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
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

    /** 对纯问候、感谢和能力询问执行整句白名单路由。 */
    private final SmallTalkRouter smallTalkRouter;

    /** 在概率分类前处理写操作、企业知识和实时查询等确定性策略。 */
    private final DeterministicPolicyRouter deterministicPolicyRouter;

    /** 与 FastAPI 共享的分类置信度阈值。 */
    private final IntentRoutingProperties intentRoutingProperties;

    /**
     * FastAPI RAG 客户端。
     *
     * 只在 ask() 的权限和文档归属校验全部通过后调用，避免无权限请求浪费模型调用。
     */
    private final FastApiRagClient fastApiRagClient;

    /**
     * AI 接口限流服务。
     *
     * 放在权限校验之后、FastAPI 调用之前，避免无权限请求浪费限流次数。
     */
    private final AiRateLimitService aiRateLimitService;

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
            SmallTalkRouter smallTalkRouter,
            DeterministicPolicyRouter deterministicPolicyRouter,
            IntentRoutingProperties intentRoutingProperties,
            FastApiRagClient fastApiRagClient,
            AiRateLimitService aiRateLimitService,
            ObjectMapper objectMapper
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.smallTalkRouter = smallTalkRouter;
        this.deterministicPolicyRouter = deterministicPolicyRouter;
        this.intentRoutingProperties = intentRoutingProperties;
        this.fastApiRagClient = fastApiRagClient;
        this.aiRateLimitService = aiRateLimitService;
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
        String userId = requireUserId(request.userId());
        String department = requireDepartment(request.department());
        knowledgeBaseService.getRequiredWithAccess(request.knowledgeBaseId(), userId, department);

        ChatSession chatSession = new ChatSession(
                UUID.randomUUID(),
                request.knowledgeBaseId(),
                userId,
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
        return chatSessionRepository.findByUserIdOrderByCreatedAtDesc(requireUserId(userId));
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
        StreamingAsk prepared = prepareStreamingAsk(
                sessionId,
                userId,
                department,
                question,
                documentId
        );

        if (prepared.routeDecision().directAnswer() != null) {
            String answer = prepared.routeDecision().directAnswer();
            saveAssistantMessage(
                    prepared.session(),
                    answer,
                    null,
                    prepared.routeDecision().route().auditMode(),
                    null,
                    toRoutingDecisionJson(prepared.routeDecision())
            );
            return new FastApiRagResponse(
                    question,
                    answer,
                    List.of(),
                    prepared.routeDecision().route().auditMode(),
                    null,
                    null,
                    null
            );
        }

        if (prepared.routeDecision().route() != ChatRoute.RAG) {
            return answerWithoutKnowledgeBase(prepared);
        }

        FastApiRagResponse response = fastApiRagClient.askWithRerank(question, documentId);
        saveAssistantMessage(
                prepared.session(),
                response.answer(),
                toSourcesJson(response),
                response.retrievalMode(),
                response.rerankElapsedSeconds(),
                toRoutingDecisionJson(prepared.routeDecision())
        );
        return response;
    }

    /**
     * 在返回流式响应之前完成权限、文档归属、限流校验并立即保存用户消息。
     *
     * 这样无权限请求仍会在响应头发出前得到标准 JSON 错误；合法请求则可以让前端
     * 立刻显示已经进入聊天记录的用户问题。
     */
    public StreamingAsk prepareStreamingAsk(UUID sessionId,
                                            String userId,
                                            String department,
                                            String question,
                                            String documentId) {
        ChatSession session = getRequiredSessionWithAccess(sessionId, userId, department);
        validateDocumentBelongsToSessionKnowledgeBase(documentId, session.knowledgeBaseId());
        Optional<SmallTalkRouter.SmallTalkReply> smallTalkReply = smallTalkRouter.route(question);
        ChatRouteDecision routeDecision;
        if (smallTalkReply.isPresent()) {
            SmallTalkRouter.SmallTalkReply reply = smallTalkReply.orElseThrow();
            routeDecision = new ChatRouteDecision(
                    ChatRoute.SMALL_TALK,
                    reply.answer(),
                    "small_talk_rule",
                    reply.intent().name().toLowerCase(java.util.Locale.ROOT),
                    null,
                    false,
                    null
            );
        } else {
            Optional<DeterministicPolicyRouter.PolicyDecision> policyDecision =
                    deterministicPolicyRouter.route(question);
            if (policyDecision.isPresent()) {
                routeDecision = fromPolicyDecision(policyDecision.orElseThrow());
                if (routeDecision.route().requiresAi()) {
                    aiRateLimitService.checkAiCallAllowed(userId, "RAG_CHAT");
                }
            } else {
                aiRateLimitService.checkAiCallAllowed(userId, "RAG_CHAT");
                routeDecision = decideRoute(question);
            }
        }

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
        ChatMessage savedUserMessage = chatMessageRepository.save(userMessage);
        return new StreamingAsk(
                session,
                savedUserMessage,
                question,
                documentId,
                routeDecision
        );
    }

    /**
     * 消费 FastAPI NDJSON 事件，向浏览器转发状态和文本增量，并在完成后保存 AI 消息。
     */
    public void streamAnswer(StreamingAsk prepared, Consumer<JsonNode> browserEventConsumer) {
        if (prepared.routeDecision().directAnswer() != null) {
            streamDirectAnswer(
                    prepared,
                    prepared.routeDecision().directAnswer(),
                    browserEventConsumer
            );
            return;
        }

        if (prepared.routeDecision().route() != ChatRoute.RAG) {
            streamAnswerWithoutKnowledgeBase(prepared, browserEventConsumer);
            return;
        }

        StringBuilder answer = new StringBuilder();
        JsonNode[] sources = {objectMapper.createArrayNode()};
        String[] retrievalMode = {null};
        Double[] rerankElapsedSeconds = {null};
        boolean[] completed = {false};

        fastApiRagClient.streamAskWithRerank(
                prepared.question(),
                prepared.documentId(),
                event -> {
                    String type = event.path("type").asText("");
                    if ("delta".equals(type)) {
                        answer.append(event.path("content").asText(""));
                        browserEventConsumer.accept(event);
                        return;
                    }
                    if ("replace".equals(type)) {
                        answer.setLength(0);
                        answer.append(event.path("content").asText(""));
                        browserEventConsumer.accept(event);
                        return;
                    }
                    if ("sources".equals(type)) {
                        sources[0] = event.path("sources");
                        retrievalMode[0] = nullableText(event, "retrieval_mode");
                        rerankElapsedSeconds[0] = nullableDouble(event, "rerank_elapsed_seconds");
                        browserEventConsumer.accept(event);
                        return;
                    }
                    if ("done".equals(type)) {
                        completed[0] = true;
                        if (retrievalMode[0] == null) {
                            retrievalMode[0] = nullableText(event, "retrieval_mode");
                        }
                        if (rerankElapsedSeconds[0] == null) {
                            rerankElapsedSeconds[0] = nullableDouble(event, "rerank_elapsed_seconds");
                        }
                        return;
                    }
                    if ("error".equals(type)) {
                        throw new BusinessException(
                                event.path("message").asText("AI 回答生成失败，请稍后重试")
                        );
                    }
                    browserEventConsumer.accept(event);
                }
        );

        if (!completed[0]) {
            throw new BusinessException("AI 回答流意外中断，请重新提问");
        }
        if (answer.toString().isBlank()) {
            throw new BusinessException("AI 未返回有效回答，请重新提问");
        }

        ChatMessage assistantMessage = saveAssistantMessage(
                prepared.session(),
                answer.toString(),
                toSourcesJson(sources[0]),
                retrievalMode[0],
                rerankElapsedSeconds[0],
                toRoutingDecisionJson(prepared.routeDecision())
        );
        var doneEvent = objectMapper.createObjectNode();
        doneEvent.put("type", "done");
        doneEvent.set("message", objectMapper.valueToTree(ChatMessageResponse.from(assistantMessage)));
        browserEventConsumer.accept(doneEvent);
    }

    private void streamDirectAnswer(
            StreamingAsk prepared,
            String answer,
            Consumer<JsonNode> browserEventConsumer
    ) {
        ChatMessage assistantMessage = saveAssistantMessage(
                prepared.session(),
                answer,
                null,
                prepared.routeDecision().route().auditMode(),
                null,
                toRoutingDecisionJson(prepared.routeDecision())
        );

        var deltaEvent = objectMapper.createObjectNode();
        deltaEvent.put("type", "delta");
        deltaEvent.put("content", answer);
        browserEventConsumer.accept(deltaEvent);

        var doneEvent = objectMapper.createObjectNode();
        doneEvent.put("type", "done");
        doneEvent.set("message", objectMapper.valueToTree(ChatMessageResponse.from(assistantMessage)));
        browserEventConsumer.accept(doneEvent);
    }

    private ChatRouteDecision decideRoute(String question) {
        try {
            FastApiIntentClassificationResponse classification = fastApiRagClient.classifyIntent(question);
            if (classification != null
                    && (classification.requiresConfirmation()
                    || "WRITE_TOOL".equals(classification.operation()))) {
                return fromPolicyDecision(
                        deterministicPolicyRouter.blockWriteAction("classifier_write_action"),
                        classification
                );
            }
            if (classification == null
                    || classification.intent() == null
                    || classification.enterpriseKnowledge()
                    || "ENTERPRISE".equals(classification.knowledgeScope())
                    || classification.confidence() < intentRoutingProperties.minConfidence()) {
                return ChatRouteDecision.rag(
                        classification == null ? "classifier_guard" : classification.decisionSource(),
                        classification == null ? "invalid_classifier_result" : classification.reasonCode(),
                        classification == null ? null : classification.confidence(),
                        classification == null ? null : classification.enterpriseKnowledge(),
                        classification
                );
            }
            if ("READ_TOOL".equals(classification.operation())) {
                if (!classification.missingFields().isEmpty()) {
                    return ChatRouteDecision.classified(ChatRoute.CLARIFICATION, classification);
                }
                return fromPolicyDecision(
                        deterministicPolicyRouter.realtimeToolUnavailable(
                                "classifier_realtime_tool_unavailable"
                        ),
                        classification
                );
            }
            if ("REALTIME".equals(classification.freshness())) {
                return fromPolicyDecision(
                        deterministicPolicyRouter.realtimeToolUnavailable(
                                "classifier_realtime_tool_unavailable"
                        ),
                        classification
                );
            }
            return switch (classification.intent()) {
                case "OPEN_DOMAIN_CHAT" -> ChatRouteDecision.classified(
                        ChatRoute.OPEN_DOMAIN_CHAT,
                        classification
                );
                case "TOOL_CALL" -> ChatRouteDecision.classified(ChatRoute.TOOL_CALL, classification);
                case "CLARIFICATION" -> ChatRouteDecision.classified(
                        ChatRoute.CLARIFICATION,
                        classification
                );
                default -> ChatRouteDecision.rag(
                        "classifier_guard",
                        "unknown_intent",
                        classification.confidence(),
                        classification.enterpriseKnowledge(),
                        classification
                );
            };
        } catch (BusinessException exception) {
            log.warn("Intent classification failed; conservatively routing to RAG: {}", exception.getMessage());
            return ChatRouteDecision.rag(
                    "classifier_fallback",
                    "classifier_unavailable",
                    null,
                    null,
                    null
            );
        }
    }

    private ChatRouteDecision fromPolicyDecision(
            DeterministicPolicyRouter.PolicyDecision decision
    ) {
        return fromPolicyDecision(decision, null);
    }

    private ChatRouteDecision fromPolicyDecision(
            DeterministicPolicyRouter.PolicyDecision decision,
            FastApiIntentClassificationResponse classification
    ) {
        if (decision.route() == DeterministicPolicyRouter.PolicyRoute.RAG) {
            return ChatRouteDecision.rag(
                    "deterministic_policy",
                    decision.reasonCode(),
                    null,
                    true,
                    null
            );
        }
        ChatRoute route = switch (decision.auditMode()) {
            case "destructive_action_blocked" -> ChatRoute.DESTRUCTIVE_ACTION_BLOCKED;
            case "realtime_tool_unavailable" -> ChatRoute.REALTIME_TOOL_UNAVAILABLE;
            default -> throw new IllegalArgumentException(
                    "Unsupported deterministic policy mode: " + decision.auditMode()
            );
        };
        return new ChatRouteDecision(
                route,
                decision.answer(),
                classification == null ? "deterministic_policy" : classification.decisionSource(),
                decision.reasonCode(),
                classification == null ? null : classification.confidence(),
                classification == null ? false : classification.enterpriseKnowledge(),
                classification
        );
    }

    private FastApiRagResponse answerWithoutKnowledgeBase(StreamingAsk prepared) {
        FastApiChatResponse response = fastApiRagClient.chatWithoutKnowledgeBase(
                prepared.question(),
                prepared.routeDecision().route().apiMode()
        );
        saveAssistantMessage(
                prepared.session(),
                response.answer(),
                null,
                prepared.routeDecision().route().auditMode(),
                null,
                toRoutingDecisionJson(prepared.routeDecision())
        );
        return new FastApiRagResponse(
                prepared.question(),
                response.answer(),
                List.of(),
                prepared.routeDecision().route().auditMode(),
                null,
                null,
                null,
                response.modelUsage()
        );
    }

    private void streamAnswerWithoutKnowledgeBase(
            StreamingAsk prepared,
            Consumer<JsonNode> browserEventConsumer
    ) {
        var statusEvent = objectMapper.createObjectNode();
        statusEvent.put("type", "status");
        statusEvent.put("stage", "routing");
        statusEvent.put("message", "已识别为非知识库请求，正在生成回答");
        browserEventConsumer.accept(statusEvent);

        FastApiChatResponse response = fastApiRagClient.chatWithoutKnowledgeBase(
                prepared.question(),
                prepared.routeDecision().route().apiMode()
        );
        ChatMessage assistantMessage = saveAssistantMessage(
                prepared.session(),
                response.answer(),
                null,
                prepared.routeDecision().route().auditMode(),
                null,
                toRoutingDecisionJson(prepared.routeDecision())
        );

        var deltaEvent = objectMapper.createObjectNode();
        deltaEvent.put("type", "delta");
        deltaEvent.put("content", response.answer());
        browserEventConsumer.accept(deltaEvent);

        var doneEvent = objectMapper.createObjectNode();
        doneEvent.put("type", "done");
        doneEvent.set("message", objectMapper.valueToTree(ChatMessageResponse.from(assistantMessage)));
        browserEventConsumer.accept(doneEvent);
    }

    private ChatMessage saveAssistantMessage(
            ChatSession session,
            String answer,
            String sourcesJson,
            String retrievalMode,
            Double rerankElapsedSeconds,
            String routingDecisionJson
    ) {
        ChatMessage assistantMessage = new ChatMessage(
                UUID.randomUUID(),
                session.id(),
                MessageRole.ASSISTANT,
                answer,
                sourcesJson,
                retrievalMode,
                rerankElapsedSeconds,
                routingDecisionJson,
                Instant.now()
        );
        return chatMessageRepository.save(assistantMessage);
    }

    private String nullableText(JsonNode event, String fieldName) {
        JsonNode value = event.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Double nullableDouble(JsonNode event, String fieldName) {
        JsonNode value = event.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asDouble();
    }

    /**
     * 把 FastAPI 返回的 sources 转成 JSON 字符串。
     * <p>
     * sources 是一个结构化数组，里面有 filename、page_number、vector_score、rerank_score 等字段。
     * 当前先整体保存成 JSON 字符串，对应 chat_message.sources_json 这类 TEXT 字段；
     * 这样实现简单，也方便前端直接展示完整引用来源。
     */
    private String toSourcesJson(FastApiRagResponse response) {
        return toSourcesJson(objectMapper.valueToTree(response.sources()));
    }

    private String toSourcesJson(JsonNode sources) {
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception exception) {
            throw new BusinessException("保存引用来源失败", exception);
        }
    }

    private String toRoutingDecisionJson(ChatRouteDecision decision) {
        try {
            var audit = objectMapper.createObjectNode();
            audit.put("route", decision.route().name());
            audit.put("decision_source", decision.decisionSource());
            audit.put("reason_code", decision.reasonCode());
            if (decision.confidence() != null) {
                audit.put("confidence", decision.confidence());
            }
            if (decision.enterpriseKnowledge() != null) {
                audit.put("enterprise_knowledge", decision.enterpriseKnowledge());
            }
            FastApiIntentClassificationResponse classification = decision.classification();
            if (classification != null) {
                audit.put("knowledge_scope", classification.knowledgeScope());
                audit.put("operation", classification.operation());
                audit.put("freshness", classification.freshness());
                if (classification.toolName() != null) {
                    audit.put("tool_name", classification.toolName());
                }
                audit.set(
                        "missing_fields",
                        objectMapper.valueToTree(classification.missingFields())
                );
                audit.put("requires_confirmation", classification.requiresConfirmation());
            } else {
                addDeterministicRoutingAttributes(audit, decision.route());
            }
            return objectMapper.writeValueAsString(audit);
        } catch (Exception exception) {
            throw new BusinessException("保存路由审计信息失败", exception);
        }
    }

    private void addDeterministicRoutingAttributes(
            com.fasterxml.jackson.databind.node.ObjectNode audit,
            ChatRoute route
    ) {
        switch (route) {
            case RAG -> {
                audit.put("knowledge_scope", "ENTERPRISE");
                audit.put("operation", "ANSWER");
                audit.put("freshness", "STATIC");
                audit.put("requires_confirmation", false);
            }
            case DESTRUCTIVE_ACTION_BLOCKED -> {
                audit.put("knowledge_scope", "UNKNOWN");
                audit.put("operation", "WRITE_TOOL");
                audit.put("freshness", "UNKNOWN");
                audit.put("requires_confirmation", true);
            }
            case REALTIME_TOOL_UNAVAILABLE -> {
                audit.put("knowledge_scope", "PUBLIC");
                audit.put("operation", "READ_TOOL");
                audit.put("freshness", "REALTIME");
                audit.put("requires_confirmation", false);
            }
            default -> {
                audit.put("knowledge_scope", "PUBLIC");
                audit.put("operation", "ANSWER");
                audit.put("freshness", "STATIC");
                audit.put("requires_confirmation", false);
            }
        }
    }

    public record StreamingAsk(
            ChatSession session,
            ChatMessage userMessage,
            String question,
            String documentId,
            ChatRouteDecision routeDecision
    ) {
    }

    public record ChatRouteDecision(
            ChatRoute route,
            String directAnswer,
            String decisionSource,
            String reasonCode,
            Double confidence,
            Boolean enterpriseKnowledge,
            FastApiIntentClassificationResponse classification
    ) {
        private static ChatRouteDecision rag(
                String decisionSource,
                String reasonCode,
                Double confidence,
                Boolean enterpriseKnowledge,
                FastApiIntentClassificationResponse classification
        ) {
            return new ChatRouteDecision(
                    ChatRoute.RAG,
                    null,
                    decisionSource,
                    reasonCode,
                    confidence,
                    enterpriseKnowledge,
                    classification
            );
        }

        private static ChatRouteDecision classified(
                ChatRoute route,
                FastApiIntentClassificationResponse classification
        ) {
            return new ChatRouteDecision(
                    route,
                    null,
                    classification.decisionSource(),
                    classification.reasonCode(),
                    classification.confidence(),
                    classification.enterpriseKnowledge(),
                    classification
            );
        }
    }

    public enum ChatRoute {
        SMALL_TALK("small_talk", "small_talk"),
        RAG("rag", "rag"),
        OPEN_DOMAIN_CHAT("open_domain_chat", "open_domain_chat"),
        TOOL_CALL("tool_call", "tool_call"),
        CLARIFICATION("clarification", "clarification"),
        DESTRUCTIVE_ACTION_BLOCKED("destructive_action_blocked", "destructive_action_blocked"),
        REALTIME_TOOL_UNAVAILABLE("realtime_tool_unavailable", "realtime_tool_unavailable");

        private final String apiMode;
        private final String auditMode;

        ChatRoute(String apiMode, String auditMode) {
            this.apiMode = apiMode;
            this.auditMode = auditMode;
        }

        public String apiMode() {
            return apiMode;
        }

        public String auditMode() {
            return auditMode;
        }

        public boolean requiresAi() {
            return this == RAG
                    || this == OPEN_DOMAIN_CHAT
                    || this == TOOL_CALL
                    || this == CLARIFICATION;
        }
    }

}
