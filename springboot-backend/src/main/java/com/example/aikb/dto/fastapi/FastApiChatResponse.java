package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI 普通聊天响应；不包含知识库引用。 */
public record FastApiChatResponse(
        String answer,
        @JsonProperty("model_usage")
        FastApiModelUsage modelUsage
) implements FastApiUsageCarrier {
}
