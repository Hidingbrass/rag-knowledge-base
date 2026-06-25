package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.chat.AskInSessionRequest;
import com.example.aikb.dto.chat.ChatMessageResponse;
import com.example.aikb.dto.chat.ChatSessionResponse;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 聊天会话接口。
 *
 * Controller 层只负责 HTTP：
 * - 接收请求参数；
 * - 调用 ChatService；
 * - 把 Entity 转成 Response DTO；
 * - 返回统一 ApiResponse。
 *
 * 真正的业务流程，比如保存用户问题、调用 FastAPI、保存 AI 回答，都放在 ChatService。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 创建聊天会话。
     *
     * 请求体里包含 knowledgeBaseId、userId、title。
     * 创建前 ChatService 会先检查知识库是否存在。
     */
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @Valid @RequestBody CreateChatSessionRequest request
    ) {
        return ApiResponse.ok(ChatSessionResponse.from(
                chatService.createSession(request)
        ));
    }

    /**
     * 查询单个聊天会话。
     *
     * sessionId 放在路径中，表示要查询哪个会话。
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionResponse> getSession(@PathVariable UUID sessionId) {
        return ApiResponse.ok(ChatSessionResponse.from(
                chatService.getRequiredSession(sessionId)
        ));
    }

    /**
     * 查询某个会话下的消息列表。
     *
     * 返回顺序由 Repository 控制：按 createdAt 正序，也就是正常聊天顺序。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable UUID sessionId) {
        List<ChatMessageResponse> responses = chatService.listMessages(sessionId)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }

    /**
     * 在某个会话里发起一次 RAG 问答。
     *
     * sessionId：
     * - 放在 URL 中，表示“在哪个会话里提问”。
     *
     * request.question：
     * - 用户这次问的问题。
     *
     * request.documentId：
     * - FastAPI/Qdrant 的 document_id，用于限制检索范围。
     */
    @PostMapping("/sessions/{sessionId}/ask")
    public ApiResponse<FastApiRagResponse> ask(
            @PathVariable UUID sessionId,
            @Valid @RequestBody AskInSessionRequest request
    ) {
        return ApiResponse.ok(chatService.ask(
                sessionId,
                request.question(),
                request.documentId()
        ));
    }
}
