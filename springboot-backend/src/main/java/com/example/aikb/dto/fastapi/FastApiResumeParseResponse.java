package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiResumeParseResponse(
        @JsonProperty("target_roles")
        List<String> targetRoles,
        List<String> skills,
        List<FastApiResumeProject> projects,
        @JsonProperty("work_experiences")
        List<String> workExperiences,
        List<String> education,
        List<String> certifications,
        List<String> strengths,
        List<String> keywords
) {
}
