package com.example.aikb.tool;

import java.util.UUID;

public record ToolExecutionContext(
        String userId,
        String department,
        UUID sessionId
) {
}
