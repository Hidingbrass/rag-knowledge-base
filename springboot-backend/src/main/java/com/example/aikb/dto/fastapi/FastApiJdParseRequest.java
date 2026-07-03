package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FastApiJdParseRequest(
        @JsonProperty("job_description")
        String jobDescription
) {
}
