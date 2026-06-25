package com.example.aikb.entity;

import com.example.aikb.enums.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 文档业务模型。
 *
 * 这里同时保存两个 ID：
 * - id：Spring Boot 本地文档 ID，用于业务管理、列表展示、状态流转。
 * - fastApiDocumentId：FastAPI/Qdrant 返回的 document_id，用于后续 RAG 检索过滤。
 *
 * 为什么要分开：
 * Spring Boot 和 FastAPI 是两个服务，它们各自有自己的数据边界。
 * Java 业务系统不应该强行依赖 Python 服务内部怎么生成 ID。
 */
public record KnowledgeDocument(
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
}
