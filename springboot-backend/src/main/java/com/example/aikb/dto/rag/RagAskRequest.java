package com.example.aikb.dto.rag;

import jakarta.validation.constraints.NotBlank;

/**
 * Spring Boot 对外暴露的 RAG 问答请求。
 *
 * 注意：这是“业务接口 DTO”，不是 FastAPI 原始请求。
 * Spring Boot 可以在这里放 knowledgeBaseId、documentId 等业务字段，
 * 再由 Service/Client 转成 FastAPI 需要的 JSON。
 */
public record RagAskRequest(
        @NotBlank(message = "question 不能为空")
        String question,

        String documentId
) {
}
