package com.example.aikb.dto.job;

import java.time.Instant;
import java.util.UUID;

public record JobResumeVersionResponse(
        UUID id,
        String userId,
        String versionName,
        String targetRole,
        String resumeText,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
}
