package com.example.aikb.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRegisterRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 50, message = "用户名长度必须在 3 到 50 之间")
        String username,

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度必须在 6 到 100 之间")
        String password,

        @NotBlank(message = "显示名称不能为空")
        String displayName,

        @NotBlank(message = "部门不能为空")
        String department
) {
}
