package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.dto.knowledgebase.KnowledgeBaseResponse;
import com.example.aikb.dto.knowledgebase.UpdateKnowledgeBaseRequest;
import com.example.aikb.security.AuthenticatedUser;
import com.example.aikb.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.example.aikb.common.CurrentUserIdentity.departmentOrRequestParam;
import static com.example.aikb.common.CurrentUserIdentity.userIdOrRequestParam;

/**
 * 知识库管理接口。
 *
 * Controller 只做 HTTP 层的事情：
 * - 接收 JSON 请求；
 * - 调用 Service；
 * - 把业务对象转换成响应 DTO。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateKnowledgeBaseRequest request
    ) {
        String ownerId = currentUser != null ? currentUser.username() : request.ownerId();
        String department = request.department();
        if ((department == null || department.isBlank()) && currentUser != null) {
            department = currentUser.department();
        }
        return ApiResponse.ok(KnowledgeBaseResponse.from(knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                request.name(),
                request.description(),
                ownerId,
                department
        ))));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department
    ) {
        List<KnowledgeBaseResponse> responses = knowledgeBaseService.listAccessible(
                        userIdOrRequestParam(currentUser, userId),
                        departmentOrRequestParam(currentUser, department)
                )
                .stream()
                .map(KnowledgeBaseResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseResponse> get(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID knowledgeBaseId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department
    ) {
        return ApiResponse.ok(KnowledgeBaseResponse.from(
                knowledgeBaseService.getRequiredWithAccess(
                        knowledgeBaseId,
                        userIdOrRequestParam(currentUser, userId),
                        departmentOrRequestParam(currentUser, department)
                )
        ));
    }

    @PatchMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseResponse> update(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID knowledgeBaseId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request
    ) {
        return ApiResponse.ok(KnowledgeBaseResponse.from(knowledgeBaseService.update(
                knowledgeBaseId,
                userIdOrRequestParam(currentUser, userId),
                departmentOrRequestParam(currentUser, department),
                request
        )));
    }

    @DeleteMapping("/{knowledgeBaseId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID knowledgeBaseId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department
    ) {
        knowledgeBaseService.delete(
                knowledgeBaseId,
                userIdOrRequestParam(currentUser, userId),
                departmentOrRequestParam(currentUser, department)
        );
        return ApiResponse.ok(null);
    }
}
