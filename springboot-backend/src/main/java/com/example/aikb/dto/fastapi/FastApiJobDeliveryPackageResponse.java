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
        List<String> rehearsalChecklist
) {
}
