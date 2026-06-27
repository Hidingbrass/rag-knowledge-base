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
 * 当前版本强制要求 documentId：
 * - 避免 FastAPI 在整个 Qdrant collection 里检索；
 * - 让 Spring Boot 可以先校验文档是否属于当前会话的知识库；
 * - 防止用户把其他知识库的 documentId 塞进当前会话里绕过权限。
 */
public record AskInSessionRequest(
        @NotBlank(message = "问题不能为空")
        String question,

        @NotBlank(message = "documentId 不能为空")
        String documentId
) {
}
