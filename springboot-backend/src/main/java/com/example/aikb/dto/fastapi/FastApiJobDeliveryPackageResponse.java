package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiJobDeliveryPackageResponse(
        @JsonProperty("target_position")
        String targetPosition,

        @JsonProperty("self_introduction")
        String selfIntroduction,

        @JsonProperty("project_pitch")
        String projectPitch,

        @JsonProperty("architecture_talking_points")
        List<String> architectureTalkingPoints,

        @JsonProperty("risk_response")
        List<String> riskResponse,

        @JsonProperty("closing_statement")
        String closingStatement,

        @JsonProperty("rehearsal_checklist")
        List<String> rehearsalChecklist,

        @JsonProperty("model_usage")
        FastApiModelUsage modelUsage
) implements FastApiUsageCarrier {
    public FastApiJobDeliveryPackageResponse(
            String targetPosition,
            String selfIntroduction,
            String projectPitch,
            List<String> architectureTalkingPoints,
            List<String> riskResponse,
            String closingStatement,
            List<String> rehearsalChecklist
    ) {
        this(targetPosition, selfIntroduction, projectPitch, architectureTalkingPoints,
                riskResponse, closingStatement, rehearsalChecklist, null);
    }
}
