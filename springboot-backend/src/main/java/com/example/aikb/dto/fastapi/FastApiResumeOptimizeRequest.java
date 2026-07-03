package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiResumeOptimizeRequest(
        @JsonProperty("resume_text")
        String resumeText,

        @JsonProperty("job_description")
        String jobDescription
) {
}
