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
        name = "job_favorite",
        indexes = {
                @Index(name = "idx_job_favorite_user_created_at", columnList = "user_id,created_at")
        }
)
public class JobFavorite {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String jobTitle;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String jobDescription;

    @Column
    private String sourceUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    private Instant createdAt;

    protected JobFavorite() {
    }

    public JobFavorite(
            UUID id,
            String userId,
            String jobTitle,
            String companyName,
            String jobDescription,
            String sourceUrl,
            String notes,
            Instant createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.jobTitle = jobTitle;
        this.companyName = companyName;
        this.jobDescription = jobDescription;
        this.sourceUrl = sourceUrl;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String userId() {
        return userId;
    }

    public String jobTitle() {
        return jobTitle;
    }

    public String companyName() {
        return companyName;
    }

    public String jobDescription() {
        return jobDescription;
    }

    public String sourceUrl() {
        return sourceUrl;
    }

    public String notes() {
        return notes;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
