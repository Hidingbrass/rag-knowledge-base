package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.dto.knowledgebase.UpdateKnowledgeBaseRequest;
import com.example.aikb.entity.ChatSession;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.ChatMessageRepository;
import com.example.aikb.repository.ChatSessionRepository;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import com.example.aikb.repository.KnowledgeBaseRepository;
import com.example.aikb.repository.ToolActionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.example.aikb.common.RequestIdentity.requireDepartment;
import static com.example.aikb.common.RequestIdentity.requireUserId;

/**
 * 知识库业务服务。
 *
 * Service 层负责业务规则：
 * - 创建知识库时生成业务 ID 和创建时间；
 * - 查询知识库不存在时抛出业务异常；
 * - 统一校验当前用户是否能访问某个知识库。
 *
 * Controller 不直接写权限判断，避免同一条规则散落在多个 HTTP 接口里。
 */
@Service
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository repository;
    private final KnowledgeDocumentRepository documentRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ToolActionRepository toolActionRepository;
    private final FastApiRagClient fastApiRagClient;

    public KnowledgeBaseService(
            KnowledgeBaseRepository repository,
            KnowledgeDocumentRepository documentRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            ToolActionRepository toolActionRepository,
            FastApiRagClient fastApiRagClient
    ) {
        this.repository = repository;
        this.documentRepository = documentRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.toolActionRepository = toolActionRepository;
        this.fastApiRagClient = fastApiRagClient;
    }

    public KnowledgeBase create(CreateKnowledgeBaseRequest request) {
        KnowledgeBase knowledgeBase = new KnowledgeBase(
                UUID.randomUUID(),
                requireName(request.name()),
                normalizeDescription(request.description()),
                requireUserId(request.ownerId()),
                requireDepartment(request.department()),
                Instant.now()
        );

        return repository.save(knowledgeBase);
    }

    public KnowledgeBase getRequired(UUID knowledgeBaseId) {
        return repository.findById(knowledgeBaseId)
                .orElseThrow(() -> new BusinessException("知识库不存在: " + knowledgeBaseId));
    }

    /**
     * 查询知识库，并校验当前用户是否有访问权限。
     *
     * 个人学习版规则：只有创建者本人可以访问自己的知识库。
     * department 继续作为学习方向元数据保留，但不再参与权限判断。
     */
    public KnowledgeBase getRequiredWithAccess(UUID knowledgeBaseId, String userId, String department) {
        String requiredUserId = requireUserId(userId);
        // 保留旧接口的 department 必填校验，避免破坏已有客户端契约；授权只看 ownerId。
        requireDepartment(department);
        KnowledgeBase knowledgeBase = getRequired(knowledgeBaseId);
        if (!knowledgeBase.ownerId().equals(requiredUserId)) {
            throw new ForbiddenException("无权访问知识库: " + knowledgeBaseId);
        }
        return knowledgeBase;
    }

    public List<KnowledgeBase> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 只返回当前用户自己创建的知识库。
     * department 参数仅为旧接口兼容保留，不参与查询过滤。
     */
    public List<KnowledgeBase> listAccessible(String userId, String department) {
        String requiredUserId = requireUserId(userId);
        requireDepartment(department);
        return repository.findByOwnerIdOrderByCreatedAtDesc(requiredUserId);
    }

    @Transactional
    public KnowledgeBase update(
            UUID knowledgeBaseId,
            String userId,
            String department,
            UpdateKnowledgeBaseRequest request
    ) {
        KnowledgeBase knowledgeBase = getRequiredWithAccess(knowledgeBaseId, userId, department);
        knowledgeBase.updateDetails(
                requireName(request.name()),
                normalizeDescription(request.description()),
                requireDepartment(request.department())
        );
        return repository.save(knowledgeBase);
    }

    /**
     * 删除个人资料库及其完整关联数据。
     *
     * Qdrant 删除接口是幂等的，因此先删除向量；如果后续数据库事务失败，重试仍然安全。
     * MySQL 按“工具操作 -> 消息 -> 会话 -> 文档 -> 资料库”顺序删除，避免留下孤立业务数据。
     */
    @Transactional
    public void delete(UUID knowledgeBaseId, String userId, String department) {
        KnowledgeBase knowledgeBase = getRequiredWithAccess(knowledgeBaseId, userId, department);
        List<KnowledgeDocument> documents = documentRepository
                .findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId);

        documents.stream()
                .map(KnowledgeDocument::fastApiDocumentId)
                .filter(documentId -> documentId != null && !documentId.isBlank())
                .distinct()
                .forEach(fastApiRagClient::deleteDocument);

        List<UUID> sessionIds = chatSessionRepository.findByKnowledgeBaseId(knowledgeBaseId)
                .stream()
                .map(ChatSession::id)
                .toList();
        if (!sessionIds.isEmpty()) {
            toolActionRepository.deleteBySessionIdIn(sessionIds);
            chatMessageRepository.deleteBySessionIdIn(sessionIds);
        }
        chatSessionRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
        documentRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
        repository.delete(knowledgeBase);
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("知识库名称不能为空");
        }
        return name.trim();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

}
