package com.example.aikb.dto.job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JobTaskCompareItem(
        UUID taskId,
        int matchScore,
        String jobDescription,
        List<String> matchedSkills,
        List<String> missingSkills,
        List<String> strengths,
        List<String> risks,
        Instant createdAt
) {
}
