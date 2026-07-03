package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiProjectTalkingPoint(
        @JsonProperty("project_name")
        String projectName,
        String pitch,
        @JsonProperty("technical_depth")
        List<String> technicalDepth,
        @JsonProperty("likely_followups")
        List<String> likelyFollowups
) {
}
