package com.example.aikb.dto.knowledgebase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        @Size(max = 100, message = "知识库名称不能超过 100 个字符")
        String name,

        @Size(max = 2000, message = "知识库描述不能超过 2000 个字符")
        String description,

        @NotBlank(message = "学习方向不能为空")
        @Size(max = 100, message = "学习方向不能超过 100 个字符")
        String department
) {
}
