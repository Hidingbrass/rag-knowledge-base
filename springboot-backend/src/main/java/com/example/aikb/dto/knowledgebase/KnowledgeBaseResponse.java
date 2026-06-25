package com.example.aikb.dto.knowledgebase;

import com.example.aikb.entity.KnowledgeBase;

import java.time.Instant;
import java.util.UUID;

/**
 * 知识库响应 DTO。
 *
 * Controller 返回 DTO，而不是直接把内部模型暴露出去。
 * 这样后续内部字段变化时，对前端接口影响更小。
 */
public record KnowledgeBaseResponse(
        UUID id,
        String name,
        String description,
        String ownerId,
        String department,
        Instant createdAt
) {

    public static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.id(),
                knowledgeBase.name(),
                knowledgeBase.description(),
                knowledgeBase.ownerId(),
                knowledgeBase.department(),
                knowledgeBase.createdAt()
        );
    }
}
