package com.example.aikb.dto.job;

import jakarta.validation.constraints.NotBlank;

public record JobResumeVersionRequest(
        String userId,

        @NotBlank(message = "版本名称不能为空")
        String versionName,

        @NotBlank(message = "目标岗位不能为空")
        String targetRole,

        @NotBlank(message = "简历内容不能为空")
        String resumeText,

        String notes
) {
}
