package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiStarInterviewAnswerRequest(
        @JsonProperty("resume_text")
        String resumeText,
        @JsonProperty("job_description")
        String jobDescription,
        String question
) {
}
