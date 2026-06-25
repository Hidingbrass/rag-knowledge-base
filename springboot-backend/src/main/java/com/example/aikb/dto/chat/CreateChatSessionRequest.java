package com.example.aikb.dto.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * 创建聊天会话的请求 DTO。
 *
 * 前端创建一个新会话时，只需要告诉后端：
 * - 使用哪个知识库；
 * - 是哪个用户创建；
 * - 会话标题是什么。
 */
public record CreateChatSessionRequest(
        @NotNull(message = "知识库 ID 不能为空")
        UUID knowledgeBaseId,

        @NotBlank(message = "用户 ID 不能为空")
        String userId,

        @NotBlank(message = "会话标题不能为空")
        String title
) {
}
