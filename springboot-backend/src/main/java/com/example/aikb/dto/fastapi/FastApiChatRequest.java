package com.example.aikb.dto.fastapi;

/** 不使用知识库检索的受控聊天请求。 */
public record FastApiChatRequest(
        String question,
        String mode
) {
}
