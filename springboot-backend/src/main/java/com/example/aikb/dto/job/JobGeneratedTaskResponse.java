package com.example.aikb.dto.job;

import com.example.aikb.entity.JobGeneratedTaskType;
import com.example.aikb.entity.JobTaskStatus;
import com.example.aikb.entity.JobReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record JobGeneratedTaskResponse(
        UUID id,
        String userId,
        JobGeneratedTaskType taskType,
        JobTaskStatus status,
        String errorMessage,
        JobReviewStatus reviewStatus,
        String reviewComment,
        Instant reviewedAt,
        String resumeText,
        String jobDescription,
        String resultJson,
        Instant createdAt
) {
}
