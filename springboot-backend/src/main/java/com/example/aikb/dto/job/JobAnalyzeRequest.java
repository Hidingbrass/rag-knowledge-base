package com.example.aikb.dto.job;

import jakarta.validation.constraints.NotBlank;

/**
 * Spring Boot 求职分析入口请求 DTO。
 * <p>
 * 这个 DTO 面向前端，所以字段使用 Java 常见的 camelCase。
 */
public record JobAnalyzeRequest(
        @NotBlank(message = "用户 ID 不能为空")
        String userId,

        @NotBlank(message = "简历内容不能为空")
        String resumeText,

        @NotBlank(message = "岗位 JD 不能为空")
        String jobDescription
) {
}
