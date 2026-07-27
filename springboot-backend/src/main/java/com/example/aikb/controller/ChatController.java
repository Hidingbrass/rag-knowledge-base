package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.chat.AskInSessionRequest;
import com.example.aikb.dto.chat.ChatMessageResponse;
import com.example.aikb.dto.chat.ChatSessionResponse;
import com.example.aikb.dto.chat.CreateChatSessionRequest;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.security.AuthenticatedUser;
import com.example.aikb.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static com.example.aikb.common.CurrentUserIdentity.departmentOrRequestParam;
import static com.example.aikb.common.CurrentUserIdentity.userIdOrRequestParam;

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
 * 生产环境从 JWT / SecurityContext 读取用户身份；userId + department 仅保留给显式开启的 legacy 调试模式。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ChatController(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建聊天会话。
     * <p>
     * 请求体里包含 knowledgeBaseId、userId、department、title。
     * 创建前 ChatService 会先校验当前用户能访问这个知识库。
     */
    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateChatSessionRequest request
    ) {
        return ApiResponse.ok(ChatSessionResponse.from(
                chatService.createSession(new CreateChatSessionRequest(
                        request.knowledgeBaseId(),
                        userIdOrRequestParam(currentUser, request.userId()),
                        departmentOrRequestParam(currentUser, request.department()),
                        request.title()
                ))
        ));
    }

    /**
     * 查询单个聊天会话。
     * <p>
     * sessionId 放在路径中，表示要查询哪个会话。
     * userId + department 用来校验当前用户是否能访问该会话所属知识库。
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<ChatSessionResponse> getSession(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department
    ) {
        return ApiResponse.ok(ChatSessionResponse.from(
                chatService.getRequiredSessionWithAccess(
                        sessionId,
                        userIdOrRequestParam(currentUser, userId),
                        departmentOrRequestParam(currentUser, department)
                )
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
    public ApiResponse<List<ChatSessionResponse>> listSessions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId
    ) {
        List<ChatSessionResponse> responses = chatService.listSessionsByUserId(userIdOrRequestParam(currentUser, userId))
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
    public ApiResponse<List<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department
    ) {
        List<ChatMessageResponse> responses = chatService.listMessages(
                        sessionId,
                        userIdOrRequestParam(currentUser, userId),
                        departmentOrRequestParam(currentUser, department)
                )
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
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @Valid @RequestBody AskInSessionRequest request
    ) {
        return ApiResponse.ok(chatService.ask(
                sessionId,
                userIdOrRequestParam(currentUser, userId),
                departmentOrRequestParam(currentUser, department),
                request.question(),
                request.documentId()
        ));
    }

    /**
     * 流式 RAG 问答入口。
     *
     * 权限、文档归属和限流校验在响应头发送前完成；通过后以 NDJSON 逐行返回
     * accepted/status/sources/delta/done/error 事件。
     */
    @PostMapping(
            value = "/sessions/{sessionId}/ask/stream",
            produces = "application/x-ndjson"
    )
    public ResponseEntity<StreamingResponseBody> askStream(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @Valid @RequestBody AskInSessionRequest request
    ) {
        ChatService.StreamingAsk prepared = chatService.prepareStreamingAsk(
                sessionId,
                userIdOrRequestParam(currentUser, userId),
                departmentOrRequestParam(currentUser, department),
                request.question(),
                request.documentId()
        );

        StreamingResponseBody stream = outputStream -> {
            try {
                var acceptedEvent = objectMapper.createObjectNode();
                acceptedEvent.put("type", "accepted");
                acceptedEvent.set(
                        "message",
                        objectMapper.valueToTree(ChatMessageResponse.from(prepared.userMessage()))
                );
                writeEvent(outputStream, acceptedEvent);

                chatService.streamAnswer(
                        prepared,
                        event -> {
                            try {
                                writeEvent(outputStream, event);
                            } catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        }
                );
            } catch (UncheckedIOException exception) {
                throw exception.getCause();
            } catch (Exception exception) {
                String message = exception instanceof BusinessException
                        ? exception.getMessage()
                        : "AI 回答生成失败，请稍后重试";
                var errorEvent = objectMapper.createObjectNode();
                errorEvent.put("type", "error");
                errorEvent.put("message", message);
                writeEvent(outputStream, errorEvent);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .body(stream);
    }

    private void writeEvent(OutputStream outputStream, JsonNode event) throws IOException {
        outputStream.write(objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8));
        outputStream.write('\n');
        outputStream.flush();
    }
}
