package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * FastAPI /rag/chat/rerank 的响应结构。
 *
 * Spring Boot 后续会把 answer 存成聊天消息，
 * 把 sources 存成 JSON，方便用户查看引用来源。
 */
public record FastApiRagResponse(
        String question,
        String answer,
        List<FastApiSource> sources,

        @JsonProperty("retrieval_mode")
        String retrievalMode,

        @JsonProperty("rerank_error")
        String rerankError,

        @JsonProperty("rerank_elapsed_seconds")
        Double rerankElapsedSeconds
) {
}
