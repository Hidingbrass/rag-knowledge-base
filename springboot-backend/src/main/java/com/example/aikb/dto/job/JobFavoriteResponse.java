package com.example.aikb.dto.job;

import java.time.Instant;
import java.util.UUID;

public record JobFavoriteResponse(
        UUID id,
        String userId,
        String jobTitle,
        String companyName,
        String jobDescription,
        String sourceUrl,
        String notes,
        Instant createdAt
) {
}
