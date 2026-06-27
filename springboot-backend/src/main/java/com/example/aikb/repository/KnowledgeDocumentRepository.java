package com.example.aikb.repository;

import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档 JPA Repository。
 *
 * 这里负责查询 MySQL 里的文档业务记录。
 * 真正的向量片段仍然在 Qdrant 中，不会保存在这张表里。
 */
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findByKnowledgeBaseIdOrderByCreatedAtDesc(UUID knowledgeBaseId);

    /**
     * 根据 FastAPI/Qdrant 返回的 document_id 查询 Spring Boot 业务文档。
     *
     * ChatService 用它来判断：前端传入的 documentId 是否真的属于当前会话的知识库。
     */
    Optional<KnowledgeDocument> findFirstByFastApiDocumentId(String fastApiDocumentId);

    /**
     * 在同一个知识库内查找同 hash、指定状态范围内的最近文档。
     *
     * DocumentService 用它做重复上传检测：
     * PROCESSING / AVAILABLE 可以复用，FAILED 不复用。
     */
    Optional<KnowledgeDocument> findFirstByKnowledgeBaseIdAndFileHashAndStatusInOrderByCreatedAtDesc(
            UUID knowledgeBaseId,
            String fileHash,
            Collection<DocumentStatus> statuses
    );
}
