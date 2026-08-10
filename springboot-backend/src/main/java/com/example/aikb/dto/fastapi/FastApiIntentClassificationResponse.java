package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 规则未命中后，FastAPI 轻量分类器返回的保守路由结果。 */
public record FastApiIntentClassificationResponse(
        String intent,
        double confidence,
        @JsonProperty("enterprise_knowledge")
        boolean enterpriseKnowledge,
        @JsonProperty("reason_code")
        String reasonCode,
        @JsonProperty("decision_source")
        String decisionSource,
        @JsonProperty("knowledge_scope")
        String knowledgeScope,
        String operation,
        String freshness,
        @JsonProperty("tool_name")
        String toolName,
        @JsonProperty("missing_fields")
        List<String> missingFields,
        @JsonProperty("requires_confirmation")
        boolean requiresConfirmation,
        @JsonProperty("model_usage")
        FastApiModelUsage modelUsage
) implements FastApiUsageCarrier {
    public FastApiIntentClassificationResponse(
            String intent,
            double confidence,
            boolean enterpriseKnowledge,
            String reasonCode,
            String decisionSource,
            FastApiModelUsage modelUsage
    ) {
        this(
                intent,
                confidence,
                enterpriseKnowledge,
                reasonCode,
                decisionSource,
                enterpriseKnowledge
                        ? "ENTERPRISE"
                        : ("OPEN_DOMAIN_CHAT".equals(intent) ? "PUBLIC" : "UNKNOWN"),
                "ANSWER",
                "UNKNOWN",
                null,
                List.of(),
                false,
                modelUsage
        );
    }

    public FastApiIntentClassificationResponse {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
