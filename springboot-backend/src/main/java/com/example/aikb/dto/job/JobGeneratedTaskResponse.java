package com.example.aikb.dto.job;

import com.example.aikb.entity.JobGeneratedTaskType;

import java.time.Instant;
import java.util.UUID;

public record JobGeneratedTaskResponse(
        UUID id,
        String userId,
        JobGeneratedTaskType taskType,
        String resumeText,
        String jobDescription,
        String resultJson,
        Instant createdAt
) {
}
