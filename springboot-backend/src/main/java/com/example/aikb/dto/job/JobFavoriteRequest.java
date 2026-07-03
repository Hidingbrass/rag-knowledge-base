package com.example.aikb.dto.job;

import jakarta.validation.constraints.NotBlank;

public record JobFavoriteRequest(
        @NotBlank(message = "用户 ID 不能为空")
        String userId,

        @NotBlank(message = "岗位名称不能为空")
        String jobTitle,

        @NotBlank(message = "公司名称不能为空")
        String companyName,

        @NotBlank(message = "岗位 JD 不能为空")
        String jobDescription,

        String sourceUrl,
        String notes
) {
}
