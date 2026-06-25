package com.example.aikb.dto.knowledgebase;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建知识库请求。
 *
 * DTO 只负责接口入参，不直接等同于数据库表。
 */
public record CreateKnowledgeBaseRequest(
        @NotBlank(message = "知识库名称不能为空")
        String name,

        String description,

        @NotBlank(message = "创建者不能为空")
        String ownerId,

        @NotBlank(message = "部门不能为空")
        String department
) {
}
