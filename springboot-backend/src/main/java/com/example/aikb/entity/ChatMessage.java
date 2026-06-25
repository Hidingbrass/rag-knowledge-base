package com.example.aikb.entity;

import com.example.aikb.enums.MessageRole;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天消息模型。
 *
 * 用户问题和 AI 回答都保存为 ChatMessage，通过 role 区分。
 *
 * 字段说明：
 * - id：消息 ID，后续会作为 MySQL 主键。
 * - sessionId：所属会话 ID。
 * - role：消息角色，USER 表示用户问题，ASSISTANT 表示 AI 回答。
 * - content：消息正文。
 * - sourcesJson：AI 回答引用的来源，先用 JSON 字符串保存完整结构。
 * - retrievalMode：本次回答使用的检索模式，例如 rerank 或 vector_fallback。
 * - rerankElapsedSeconds：Rerank 耗时，用户消息可以为空。
 * - createdAt：消息创建时间。
 */
public record ChatMessage(
        UUID id,
        UUID sessionId,
        MessageRole role,
        String content,
        String sourcesJson,
        String retrievalMode,
        Double rerankElapsedSeconds,
        Instant createdAt
) {
}
