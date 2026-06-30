package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * FastAPI /job/analyze 的响应结构。
 */
public record FastApiJobAnalyzeResponse(
        @JsonProperty("match_score")
        int matchScore,

        @JsonProperty("matched_skills")
        List<String> matchedSkills,

        @JsonProperty("missing_skills")
        List<String> missingSkills,

        List<String> strengths,

        List<String> risks,

        List<String> suggestions,

        @JsonProperty("interview_questions")
        List<String> interviewQuestions
) {
}