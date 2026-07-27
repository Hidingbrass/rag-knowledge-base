package com.example.aikb.dto.job;

import com.example.aikb.entity.JobGeneratedTaskType;
import com.example.aikb.entity.JobTaskStatus;

import java.time.Instant;
import java.util.UUID;

public record JobGeneratedTaskResponse(
        UUID id,
        String userId,
        JobGeneratedTaskType taskType,
        JobTaskStatus status,
        String errorMessage,
        String resumeText,
        String jobDescription,
        String resultJson,
        Instant createdAt
) {
}
