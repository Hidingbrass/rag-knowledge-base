package com.example.aikb.entity;

import com.example.aikb.enums.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.example.aikb.security.SensitiveTextConverter;

/**
 * 聊天消息数据库实体。
 *
 * 用户问题和 AI 回答都保存成 ChatMessage，通过 role 区分：
 * - USER：用户发送的问题。
 * - ASSISTANT：FastAPI RAG 返回的 AI 回答。
 *
 * sourcesJson 用 TEXT 保存完整引用来源。
 * 这样当前可以快速落库和回显；如果以后要按引用做统计、筛选或审计，可以再拆成独立表。
 */
@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String content;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String sourcesJson;

    private String retrievalMode;

    private Double rerankElapsedSeconds;

    @Column(columnDefinition = "TEXT")
    private String routingDecisionJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
        // JPA 需要无参构造方法。
    }

    public ChatMessage(
            UUID id,
            UUID sessionId,
            MessageRole role,
            String content,
            String sourcesJson,
            String retrievalMode,
            Double rerankElapsedSeconds,
            Instant createdAt
    ) {
        this(
                id,
                sessionId,
                role,
                content,
                sourcesJson,
                retrievalMode,
                rerankElapsedSeconds,
                null,
                createdAt
        );
    }

    public ChatMessage(
            UUID id,
            UUID sessionId,
            MessageRole role,
            String content,
            String sourcesJson,
            String retrievalMode,
            Double rerankElapsedSeconds,
            String routingDecisionJson,
            Instant createdAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.role = role;
        this.content = content;
        this.sourcesJson = sourcesJson;
        this.retrievalMode = retrievalMode;
        this.rerankElapsedSeconds = rerankElapsedSeconds;
        this.routingDecisionJson = routingDecisionJson;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public MessageRole role() {
        return role;
    }

    public String content() {
        return content;
    }

    public String sourcesJson() {
        return sourcesJson;
    }

    public String retrievalMode() {
        return retrievalMode;
    }

    public Double rerankElapsedSeconds() {
        return rerankElapsedSeconds;
    }

    public String routingDecisionJson() {
        return routingDecisionJson;
    }

    public void replaceToolActionResult(String updatedContent, String updatedRoutingDecisionJson) {
        this.content = updatedContent;
        this.routingDecisionJson = updatedRoutingDecisionJson;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
