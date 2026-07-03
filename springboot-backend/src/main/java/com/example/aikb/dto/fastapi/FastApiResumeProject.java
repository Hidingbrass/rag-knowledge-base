package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiResumeProject(
        String name,
        String role,
        @JsonProperty("tech_stack")
        List<String> techStack,
        String description,
        List<String> highlights
) {
}
