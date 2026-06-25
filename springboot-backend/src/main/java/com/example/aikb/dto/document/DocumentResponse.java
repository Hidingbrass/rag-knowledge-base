package com.example.aikb.dto.document;

import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * 文档响应 DTO。
 *
 * 这个对象用于给前端或 Swagger 展示文档当前状态。
 */
public record DocumentResponse(
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

    public static DocumentResponse from(KnowledgeDocument document) {
        return new DocumentResponse(
                document.id(),
                document.knowledgeBaseId(),
                document.fastApiDocumentId(),
                document.filename(),
                document.fileHash(),
                document.status(),
                document.chunkCount(),
                document.errorMessage(),
                document.createdAt(),
                document.updatedAt()
        );
    }
}
