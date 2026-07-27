package com.example.aikb.dto.job;

import jakarta.validation.constraints.NotBlank;

public record InterviewPrepRequest(
        String userId,

        @NotBlank(message = "简历内容不能为空")
        String resumeText,

        @NotBlank(message = "岗位 JD 不能为空")
        String jobDescription
) {
}
