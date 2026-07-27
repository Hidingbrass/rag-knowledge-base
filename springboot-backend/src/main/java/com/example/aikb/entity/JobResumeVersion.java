package com.example.aikb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;
import com.example.aikb.security.SensitiveTextConverter;

@Entity
@Table(
        name = "job_resume_version",
        indexes = {
                @Index(name = "idx_job_resume_version_user_updated_at", columnList = "user_id,updated_at")
        }
)
public class JobResumeVersion {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String versionName;

    @Column(nullable = false)
    private String targetRole;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String resumeText;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = SensitiveTextConverter.class)
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected JobResumeVersion() {
    }

    public JobResumeVersion(UUID id, String userId, String versionName, String targetRole, String resumeText, String notes, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.versionName = versionName;
        this.targetRole = targetRole;
        this.resumeText = resumeText;
        this.notes = notes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void update(String versionName, String targetRole, String resumeText, String notes, Instant updatedAt) {
        this.versionName = versionName;
        this.targetRole = targetRole;
        this.resumeText = resumeText;
        this.notes = notes;
        this.updatedAt = updatedAt;
    }

    public UUID id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String versionName() {
        return versionName;
    }

    public String targetRole() {
        return targetRole;
    }

    public String resumeText() {
        return resumeText;
    }

    public String notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
