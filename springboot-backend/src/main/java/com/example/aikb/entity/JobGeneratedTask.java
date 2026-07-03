package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

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
    private String resumeText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected JobGeneratedTask() {
    }

    public JobGeneratedTask(UUID id, String userId, JobGeneratedTaskType taskType, String resumeText, String jobDescription, String resultJson, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.taskType = taskType;
        this.resumeText = resumeText;
        this.jobDescription = jobDescription;
        this.resultJson = resultJson;
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

    public Instant createdAt() {
        return createdAt;
    }
}
