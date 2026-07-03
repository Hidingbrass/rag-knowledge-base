package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiResumeParseRequest(
        @JsonProperty("resume_text")
        String resumeText
) {
}
