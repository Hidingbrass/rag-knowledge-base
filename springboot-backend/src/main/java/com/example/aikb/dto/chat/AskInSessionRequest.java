package com.example.aikb.dto.chat;

import jakarta.validation.constraints.NotBlank;

/**
 * 会话内提问请求 DTO。
 *
 * 对应接口：
 * POST /api/chat/sessions/{sessionId}/ask
 *
 * 设计说明：
 * - sessionId 放在 URL 路径里，表示“在哪个会话里提问”。
 * - question 放在请求体里，表示“用户这次问什么”。
 * - documentId 放在请求体里，表示“限制 FastAPI 只检索哪份文档”。
 *
 * documentId 第一版允许为空：
 * 不传时 FastAPI 会在整个 collection 中检索；
 * 后续做权限和知识库隔离时，再强制要求传 documentId 或 knowledgeBaseId。
 */
public record AskInSessionRequest(
        @NotBlank(message = "问题不能为空")
        String question,

        String documentId
) {
}
