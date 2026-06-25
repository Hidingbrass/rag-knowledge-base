package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 调用 FastAPI /rag/chat/rerank 的请求体。
 *
 * 字段名使用 JsonProperty 对齐 Python Pydantic 的 snake_case。
 * 例如 Java 中是 candidateK，发给 FastAPI 时必须是 candidate_k。
 */
public record FastApiRerankChatRequest(
        String question,

        @JsonProperty("candidate_k")
        int candidateK,

        @JsonProperty("rerank_top_k")
        int rerankTopK,

        @JsonProperty("rerank_min_score")
        double rerankMinScore,

        @JsonProperty("retrieval_mode")
        String retrievalMode,

        @JsonProperty("keyword_limit")
        int keywordLimit,

        @JsonProperty("document_id")
        String documentId
) {
}
