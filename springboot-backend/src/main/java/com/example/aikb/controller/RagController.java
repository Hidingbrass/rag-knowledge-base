package com.example.aikb.controller;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.dto.rag.RagAskRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RAG 问答业务入口。
 *
 * 当前是 Day 1 的最小联通版本：
 * 前端或 Swagger 调 Spring Boot /api/rag/ask，
 * Spring Boot 再调用 FastAPI /rag/chat/rerank。
 *
 * 后续 Day 3 会把这里升级为：
 * - 创建聊天会话。
 * - 保存用户问题。
 * - 保存 AI 回答。
 * - 保存引用 sources。
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final FastApiRagClient fastApiRagClient;

    public RagController(FastApiRagClient fastApiRagClient) {
        this.fastApiRagClient = fastApiRagClient;
    }

    @PostMapping("/ask")
    public ApiResponse<FastApiRagResponse> ask(@Valid @RequestBody RagAskRequest request) {
        FastApiRagResponse response = fastApiRagClient.askWithRerank(
                request.question(),
                request.documentId()
        );

        return ApiResponse.ok(response);
    }
}
