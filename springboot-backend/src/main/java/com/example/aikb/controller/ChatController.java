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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 聊天会话接口。
 * <p>
 * Controller 层只负责 HTTP：
 * - 接收请求参数；
 * - 调用 ChatService；
 * - 把 Entity 转成 Response DTO；
 * - 返回统一 ApiResponse。
 * <p>
 * 真正的业务流程，比如保存用户问题、调用 FastAPI、保存 AI 回答，都放在 ChatService。
 *
 * 当前学习版暂时用 userId + department 模拟登录身份。
 * 后续接入真正登录后，这两个参数通常会从 Token / SecurityContext 中取得。
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
     * <p>
     * 请求体里包含 knowledgeBaseId、userId、department、title。
     * 创建前 ChatService 会先校验当前用户能访问这个知识库。
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
     * <p>
     * sessionId 放在路径中，表示要查询哪个会话。
     * userId + department 用来校验当前用户是否能访问该会话所属知识库。
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionResponse> getSession(@PathVariable UUID sessionId,
                                                       @RequestParam String userId,
                                                       @RequestParam String department) {
        return ApiResponse.ok(ChatSessionResponse.from(
                chatService.getRequiredSessionWithAccess(sessionId, userId, department)
        ));
    }

    /**
     * 查询某个用户的历史会话列表。
     * <p>
     * 静态前端刷新后，浏览器内存中的 sessionId 会清空。
     * 这个接口可以让前端根据 userId 从 MySQL 重新加载最近会话。
     * <p>
     * 真实项目中通常不会让前端直接传 userId，
     * 而是从登录态里拿当前用户 ID。
     */
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> listSessions(@RequestParam String userId) {
        List<ChatSessionResponse> responses = chatService.listSessionsByUserId(userId)
                .stream()
                .map(ChatSessionResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }

    /**
     * 查询某个会话下的消息列表。
     * <p>
     * 返回顺序由 Repository 控制：按 createdAt 正序，也就是正常聊天顺序。
     * 返回前会先校验当前用户是否能访问该会话，避免只凭 sessionId 读取别人的聊天记录。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> listMessages(@PathVariable UUID sessionId,
                                                               @RequestParam String userId,
                                                               @RequestParam String department) {
        List<ChatMessageResponse> responses = chatService.listMessages(sessionId, userId, department)
                .stream()
                .map(ChatMessageResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }

    /**
     * 在某个会话里发起一次 RAG 问答。
     * <p>
     * sessionId：
     * - 放在 URL 中，表示“在哪个会话里提问”。
     * <p>
     * request.question：
     * - 用户这次问的问题。
     * <p>
     * request.documentId：
     * - FastAPI/Qdrant 的 document_id，用于限制检索范围；
     * - 当前版本是必填字段；
     * - ChatService 会校验它是否属于当前会话的知识库，以及文档是否 AVAILABLE。
     */
    @PostMapping("/sessions/{sessionId}/ask")
    public ApiResponse<FastApiRagResponse> ask(
            @PathVariable UUID sessionId,
            @RequestParam String userId,
            @RequestParam String department,
            @Valid @RequestBody AskInSessionRequest request
    ) {
        return ApiResponse.ok(chatService.ask(
                sessionId,
                userId,
                department,
                request.question(),
                request.documentId()
        ));
    }
}
