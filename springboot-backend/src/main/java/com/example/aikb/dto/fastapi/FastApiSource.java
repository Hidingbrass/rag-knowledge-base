package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FastAPI 返回的引用来源。
 *
 * 这些字段来自 RAG Source：
 * - documentId / filename / pageNumber / chunkIndex：用于引用溯源。
 * - vectorScore：Dense 向量相似度。
 * - sparseScore：Sparse 词法检索分数。
 * - fusionScore：Dense + Sparse 的 RRF 融合分数。
 * - rerankScore：qwen3-rerank 相关性分数。
 */
public record FastApiSource(
        @JsonProperty("document_id")
        String documentId,

        String filename,

        @JsonProperty("page_number")
        Integer pageNumber,

        @JsonProperty("chunk_index")
        Integer chunkIndex,

        String text,

        @JsonProperty("vector_score")
        Double vectorScore,

        @JsonProperty("sparse_score")
        Double sparseScore,

        @JsonProperty("fusion_score")
        Double fusionScore,

        @JsonProperty("rerank_score")
        Double rerankScore
) {
}
