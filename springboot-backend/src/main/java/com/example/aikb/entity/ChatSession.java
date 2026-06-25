package com.example.aikb.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天会话模型。
 *
 * 一个会话表示一段连续问答，例如“研发知识库问答”。
 *
 * 字段说明：
 * - id：会话 ID，后续会作为 MySQL 主键。
 * - knowledgeBaseId：这个会话绑定的知识库，后续用于权限和检索范围控制。
 * - userId：创建会话的用户。
 * - title：会话标题，方便前端展示历史会话列表。
 * - createdAt：创建时间。
 */
public record ChatSession(
        UUID id,
        UUID knowledgeBaseId,
        String userId,
        String title,
        Instant createdAt
) {
}
