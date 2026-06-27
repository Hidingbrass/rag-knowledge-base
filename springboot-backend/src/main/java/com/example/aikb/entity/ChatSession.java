package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 聊天会话数据库实体。
 *
 * 一个会话表示一段连续问答，例如“研发知识库问答”。
 * 会话本身只保存标题、用户、知识库等元信息；真正的一问一答保存在 chat_message 表里。
 */
@Entity
@Table(name = "chat_session")
public class ChatSession {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID knowledgeBaseId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatSession() {
        // JPA 需要无参构造方法。
    }

    public ChatSession(UUID id, UUID knowledgeBaseId, String userId, String title, Instant createdAt) {
        this.id = id;
        this.knowledgeBaseId = knowledgeBaseId;
        this.userId = userId;
        this.title = title;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID knowledgeBaseId() {
        return knowledgeBaseId;
    }

    public String userId() {
        return userId;
    }

    public String title() {
        return title;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
