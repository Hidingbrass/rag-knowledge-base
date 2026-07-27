package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiJobAttachmentTextResponse(
        String filename,
        @JsonProperty("source_type")
        String sourceType,
        String text,
        List<String> warnings
) {
}
