package com.example.aikb.dto.chat;

import com.example.aikb.entity.ChatMessage;
import com.example.aikb.enums.MessageRole;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天消息响应 DTO。
 *
 * 用于返回会话中的消息列表。
 * 用户消息通常只有 content；AI 消息会额外带 sourcesJson、retrievalMode 和 rerankElapsedSeconds。
 */
public record ChatMessageResponse(
        UUID id,
        UUID sessionId,
        MessageRole role,
        String content,
        String sourcesJson,
        String retrievalMode,
        Double rerankElapsedSeconds,
        Instant createdAt
) {

    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.id(),
                message.sessionId(),
                message.role(),
                message.content(),
                message.sourcesJson(),
                message.retrievalMode(),
                message.rerankElapsedSeconds(),
                message.createdAt()
        );
    }
}
