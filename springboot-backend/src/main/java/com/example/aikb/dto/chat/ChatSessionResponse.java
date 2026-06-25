package com.example.aikb.dto.chat;

import com.example.aikb.entity.ChatSession;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天会话响应 DTO。
 *
 * Controller 返回给前端时使用这个对象，而不是直接暴露内部模型。
 */
public record ChatSessionResponse(
        UUID id,
        UUID knowledgeBaseId,
        String userId,
        String title,
        Instant createdAt
) {

    public static ChatSessionResponse from(ChatSession session) {
        return new ChatSessionResponse(
                session.id(),
                session.knowledgeBaseId(),
                session.userId(),
                session.title(),
                session.createdAt()
        );
    }
}
