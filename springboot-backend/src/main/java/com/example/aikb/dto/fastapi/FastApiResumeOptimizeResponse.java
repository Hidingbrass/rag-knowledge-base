package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiResumeOptimizeResponse(
        String summary,
        @JsonProperty("target_position")
        String targetPosition,
        @JsonProperty("gap_summary")
        List<String> gapSummary,
        @JsonProperty("rewrite_suggestions")
        List<FastApiResumeRewriteSuggestion> rewriteSuggestions,
        @JsonProperty("missing_keywords")
        List<String> missingKeywords,
        @JsonProperty("action_items")
        List<String> actionItems,
        @JsonProperty("model_usage")
        FastApiModelUsage modelUsage
) implements FastApiUsageCarrier {
    public FastApiResumeOptimizeResponse(
            String summary,
            String targetPosition,
            List<String> gapSummary,
            List<FastApiResumeRewriteSuggestion> rewriteSuggestions,
            List<String> missingKeywords,
            List<String> actionItems
    ) {
        this(summary, targetPosition, gapSummary, rewriteSuggestions, missingKeywords,
                actionItems, null);
    }
}
