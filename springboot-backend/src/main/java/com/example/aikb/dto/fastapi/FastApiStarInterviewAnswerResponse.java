package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiStarInterviewAnswerResponse(
        @JsonProperty("target_position")
        String targetPosition,
        String question,
        String situation,
        String task,
        List<String> action,
        String result,
        String answer,
        List<String> highlights,
        @JsonProperty("follow_up_questions")
        List<String> followUpQuestions
) {
}
