package com.example.aikb.tool;

import java.util.Map;

public record ToolExecutionResult(
        String answer,
        Map<String, Object> auditData
) {
    public ToolExecutionResult(String answer) {
        this(answer, Map.of());
    }
}
