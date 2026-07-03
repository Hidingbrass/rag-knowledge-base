package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiInterviewQuestionAnswer(
        String question,
        @JsonProperty("answer_points")
        List<String> answerPoints
) {
}
