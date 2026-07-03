package com.example.aikb.dto.job;

import jakarta.validation.constraints.NotBlank;

public record JdParseRequest(
        @NotBlank(message = "岗位 JD 不能为空")
        String jobDescription
) {
}
