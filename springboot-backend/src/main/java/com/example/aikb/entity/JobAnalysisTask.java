package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "job_analysis_task",
        indexes = {
                @Index(name = "idx_job_task_user_created_at", columnList = "user_id,created_at")
        }
)
public class JobAnalysisTask {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumeText;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Column(nullable = false)
    private int matchScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resultJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected JobAnalysisTask() {
    }

    public JobAnalysisTask(UUID id, String userId, String resumeText, String jobDescription, int matchScore, String resultJson, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.resumeText = resumeText;
        this.jobDescription = jobDescription;
        this.matchScore = matchScore;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String resumeText() {
        return resumeText;
    }

    public String jobDescription() {
        return jobDescription;
    }

    public int matchScore() {
        return matchScore;
    }

    public String resultJson() {
        return resultJson;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
