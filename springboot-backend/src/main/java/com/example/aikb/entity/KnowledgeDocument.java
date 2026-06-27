package com.example.aikb.entity;

import com.example.aikb.enums.DocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 文档数据库实体。
 *
 * 这张表保存 Spring Boot 侧的文档业务记录。
 * PDF 的解析、切分、向量入库仍然由 FastAPI 完成；Spring Boot 只记录业务状态和 FastAPI 返回的 document_id。
 *
 * 两个 ID 的区别：
 * - id：Spring Boot/MySQL 里的文档主键，用于业务管理、列表展示、状态流转。
 * - fastApiDocumentId：FastAPI/Qdrant 里的文档 ID，用于后续 RAG 检索时限制文档范围。
 */
@Entity
@Table(
        name = "knowledge_document",
        indexes = {
                @Index(name = "idx_document_kb_hash", columnList = "knowledge_base_id,file_hash"),
                @Index(name = "idx_document_kb_created_at", columnList = "knowledge_base_id,created_at")
        }
)
public class KnowledgeDocument {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID knowledgeBaseId;

    private String fastApiDocumentId;

    @Column(nullable = false)
    private String filename;

    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    @Column(nullable = false)
    private int chunkCount;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocument() {
        // JPA 需要无参构造方法。
    }

    public KnowledgeDocument(
            UUID id,
            UUID knowledgeBaseId,
            String fastApiDocumentId,
            String filename,
            String fileHash,
            DocumentStatus status,
            int chunkCount,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.fastApiDocumentId = fastApiDocumentId;
        this.filename = filename;
        this.fileHash = fileHash;
        this.status = status;
        this.chunkCount = chunkCount;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID id() {
        return id;
    }

    public UUID knowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String fastApiDocumentId() {
        return fastApiDocumentId;
    }

    public String filename() {
        return filename;
    }

    public String fileHash() {
        return fileHash;
    }

    public DocumentStatus status() {
        return status;
    }

    public int chunkCount() {
        return chunkCount;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
