package com.example.aikb.dto.aicall;

import java.time.Instant;
import java.util.UUID;

public record AiCallLogResponse(
        UUID id,
        String provider,
        String businessType,
        String endpoint,
        boolean success,
        long elapsedMs,
        String errorMessage,
        Instant createdAt
) {
}
