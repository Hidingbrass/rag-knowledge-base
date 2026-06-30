package com.example.aikb.dto.fastapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * 调用 FastAPI /job/analyze 的请求体。
 *
 * Java 字段是 camelCase，发给 FastAPI 时需要转成 Python/Pydantic 使用的 snake_case。
 */
public record FastApiJobAnalyzeRequest(



        @JsonProperty("resume_text")
        String resumeText,

        @JsonProperty("job_description")
        String jobDescription


) {
}