package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiResumeRewriteSuggestion(
        String section,
        String issue,
        String suggestion,
        @JsonProperty("before_text")
        String beforeText,
        @JsonProperty("after_text")
        String afterText,
        @JsonProperty("keywords_added")
        List<String> keywordsAdded
) {
}
