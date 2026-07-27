package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.example.aikb.security.SensitiveTextConverter;

@Entity
@Table(
        name = "job_generated_task",
        indexes = {
                @Index(name = "idx_job_generated_task_user_type_created_at", columnList = "user_id,task_type,created_at")
        }
)
public class JobGeneratedTask {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobGeneratedTaskType taskType;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String resumeText;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String jobDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String resultJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobTaskStatus status;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobReviewStatus reviewStatus;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String reviewComment;

    private Instant reviewedAt;

    @Column(nullable = false)
    private Instant createdAt;

    protected JobGeneratedTask() {
    }

    public JobGeneratedTask(UUID id, String userId, JobGeneratedTaskType taskType, String resumeText, String jobDescription, String resultJson, Instant createdAt) {
        this(id, userId, taskType, resumeText, jobDescription, resultJson,
                JobTaskStatus.SUCCESS, null, JobReviewStatus.PENDING_REVIEW,
                null, null, createdAt);
    }

    public JobGeneratedTask(
            UUID id,
            String userId,
            JobGeneratedTaskType taskType,
            String resumeText,
            String jobDescription,
            String resultJson,
            JobTaskStatus status,
            String errorMessage,
            Instant createdAt
    ) {
        this(id, userId, taskType, resumeText, jobDescription, resultJson, status,
                errorMessage, JobReviewStatus.PENDING_REVIEW, null, null, createdAt);
    }

    public JobGeneratedTask(
            UUID id,
            String userId,
            JobGeneratedTaskType taskType,
            String resumeText,
            String jobDescription,
            String resultJson,
            JobTaskStatus status,
            String errorMessage,
            JobReviewStatus reviewStatus,
            String reviewComment,
            Instant reviewedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.taskType = taskType;
        this.resumeText = resumeText;
        this.jobDescription = jobDescription;
        this.resultJson = resultJson;
        this.status = status;
        this.errorMessage = errorMessage;
        this.reviewStatus = reviewStatus;
        this.reviewComment = reviewComment;
        this.reviewedAt = reviewedAt;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public JobGeneratedTaskType taskType() {
        return taskType;
    }

    public String resumeText() {
        return resumeText;
    }

    public String jobDescription() {
        return jobDescription;
    }

    public String resultJson() {
        return resultJson;
    }

    public JobTaskStatus status() {
        return status;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public JobReviewStatus reviewStatus() {
        return reviewStatus;
    }

    public String reviewComment() {
        return reviewComment;
    }

    public Instant reviewedAt() {
        return reviewedAt;
    }

    public void review(JobReviewStatus status, String comment, Instant reviewedAt) {
        if (status == null || status == JobReviewStatus.PENDING_REVIEW) {
            throw new IllegalArgumentException("审核结果只能是 APPROVED 或 REJECTED");
        }
        this.reviewStatus = status;
        this.reviewComment = comment == null || comment.isBlank() ? null : comment.trim();
        this.reviewedAt = reviewedAt;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
