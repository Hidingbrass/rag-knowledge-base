package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FastApiInterviewPrepResponse(
        @JsonProperty("target_position")
        String targetPosition,
        @JsonProperty("self_introduction")
        String selfIntroduction,
        @JsonProperty("project_talking_points")
        List<FastApiProjectTalkingPoint> projectTalkingPoints,
        @JsonProperty("technical_questions")
        List<FastApiInterviewQuestionAnswer> technicalQuestions,
        @JsonProperty("behavioral_questions")
        List<FastApiInterviewQuestionAnswer> behavioralQuestions,
        @JsonProperty("questions_to_ask")
        List<String> questionsToAsk,
        @JsonProperty("preparation_checklist")
        List<String> preparationChecklist
) {
}
