package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiJdParseResponse(
        @JsonProperty("job_title")
        String jobTitle,
        String seniority,
        @JsonProperty("required_skills")
        List<String> requiredSkills,
        @JsonProperty("preferred_skills")
        List<String> preferredSkills,
        List<String> responsibilities,
        List<String> requirements,
        List<String> keywords,
        List<String> risks
) {
}
