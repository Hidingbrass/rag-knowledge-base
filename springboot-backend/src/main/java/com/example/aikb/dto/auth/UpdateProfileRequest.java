package com.example.aikb.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 100, message = "昵称不能超过 100 个字符")
        String displayName,

        @NotBlank(message = "学习方向不能为空")
        @Size(max = 100, message = "学习方向不能超过 100 个字符")
        String department
) {
}
