package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * FastAPI /documents/index 的响应结构。
 *
 * 字段来自 Python 服务：
 * - document_id：FastAPI/Qdrant 中的文档 ID。
 * - filename：原始文件名。
 * - chunk_count：切分后的片段数量。
 * - collection：Qdrant 集合名。
 * - file_hash：文件 hash，用于重复检测。
 */
public record FastApiDocumentIndexResponse(
        @JsonProperty("document_id")
        String documentId,

        String filename,

        @JsonProperty("chunk_count")
        int chunkCount,

        String collection,

        @JsonProperty("file_hash")
        String fileHash
) {
}
