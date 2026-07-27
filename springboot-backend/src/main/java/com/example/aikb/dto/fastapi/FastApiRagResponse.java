package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * FastAPI /rag/chat/rerank 的响应结构。
 *
 * Spring Boot 会把 answer 存成聊天消息，
 * 并把 sources 存成 JSON，方便用户查看引用来源。
 */
public record FastApiRagResponse(
        String question,
        String answer,
        List<FastApiSource> sources,

        @JsonProperty("retrieval_mode")
        String retrievalMode,

        @JsonProperty("candidate_retrieval_mode")
        String candidateRetrievalMode,

        @JsonProperty("rerank_error")
        String rerankError,

        @JsonProperty("rerank_elapsed_seconds")
        Double rerankElapsedSeconds,

        @JsonProperty("model_usage")
        FastApiModelUsage modelUsage
) implements FastApiUsageCarrier {
    public FastApiRagResponse(
            String question,
            String answer,
            List<FastApiSource> sources,
            String retrievalMode,
            String candidateRetrievalMode,
            String rerankError,
            Double rerankElapsedSeconds
    ) {
        this(question, answer, sources, retrievalMode, candidateRetrievalMode,
                rerankError, rerankElapsedSeconds, null);
    }
}
