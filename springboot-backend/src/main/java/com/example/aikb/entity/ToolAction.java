package com.example.aikb.entity;

import com.example.aikb.enums.ToolActionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 服务端保存的不可变写工具参数与一次性确认状态。 */
@Entity
@Table(name = "tool_action", indexes = {
        @Index(name = "idx_tool_action_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_tool_action_session_created", columnList = "session_id,created_at")
})
public class ToolAction {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String department;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private UUID assistantMessageId;

    @Column(nullable = false)
    private String toolName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String argumentsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ToolActionStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant executedAt;

    @Column(columnDefinition = "TEXT")
    private String resultSummary;

    protected ToolAction() {
    }

    public ToolAction(UUID id, String userId, String department, UUID sessionId,
                      UUID assistantMessageId, String toolName, String argumentsJson,
                      Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.department = department;
        this.sessionId = sessionId;
        this.assistantMessageId = assistantMessageId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
        this.status = ToolActionStatus.PENDING;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void markExecuted(String summary, Instant now) {
        status = ToolActionStatus.EXECUTED;
        executedAt = now;
        resultSummary = summary;
    }

    public void markExpired(Instant now) {
        status = ToolActionStatus.EXPIRED;
        executedAt = now;
        resultSummary = "确认已过期，未执行任何操作。";
    }

    public UUID id() { return id; }
    public String userId() { return userId; }
    public String department() { return department; }
    public UUID sessionId() { return sessionId; }
    public UUID assistantMessageId() { return assistantMessageId; }
    public String toolName() { return toolName; }
    public String argumentsJson() { return argumentsJson; }
    public ToolActionStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }
    public Instant executedAt() { return executedAt; }
    public String resultSummary() { return resultSummary; }
}
