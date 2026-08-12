package com.example.aikb.dto.tool;

import com.example.aikb.entity.ToolAction;
import com.example.aikb.enums.ToolActionStatus;

import java.time.Instant;
import java.util.UUID;

public record ToolActionResponse(
        UUID id,
        String toolName,
        ToolActionStatus status,
        Instant expiresAt,
        Instant executedAt,
        String result
) {
    public static ToolActionResponse from(ToolAction action) {
        return new ToolActionResponse(
                action.id(),
                action.toolName(),
                action.status(),
                action.expiresAt(),
                action.executedAt(),
                action.resultSummary()
        );
    }
}
