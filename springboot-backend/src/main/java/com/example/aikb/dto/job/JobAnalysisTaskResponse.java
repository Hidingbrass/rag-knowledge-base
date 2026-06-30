package com.example.aikb.dto.job;

import java.time.Instant;
import java.util.UUID;

public record JobAnalysisTaskResponse(
        UUID id,
        String userId,
        String resumeText,
        String jobDescription,
        int matchScore,
        String resultJson,
        Instant createdAt
) {
}
