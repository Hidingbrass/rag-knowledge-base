package com.example.aikb.dto.tool;

import jakarta.validation.constraints.NotBlank;

public record ConfirmToolActionRequest(
        @NotBlank(message = "确认文本不能为空")
        String confirmation
) {
}
