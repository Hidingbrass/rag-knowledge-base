package com.example.aikb.dto.rag;

import jakarta.validation.constraints.NotBlank;

/**
 * 旧版 Spring Boot RAG 问答请求。
 *
 * 当前保留这个 DTO 只是为了让旧接口返回明确错误提示。
 * 新的 RAG 问答统一走 /api/chat/sessions/{sessionId}/ask，
 * 由 ChatService 负责用户权限、会话权限和 documentId 归属校验。
 */
public record RagAskRequest(
        @NotBlank(message = "question 不能为空")
        String question,

        String documentId
) {
}
