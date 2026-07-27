package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.document.DocumentResponse;
import com.example.aikb.security.AuthenticatedUser;
import com.example.aikb.service.DocumentService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.example.aikb.common.CurrentUserIdentity.departmentOrRequestParam;
import static com.example.aikb.common.CurrentUserIdentity.userIdOrRequestParam;

/**
 * 文档管理接口。
 *
 * 默认从 JWT 的 AuthenticationPrincipal 读取当前用户；只有显式开启 legacy 兼容开关时，
 * 才允许旧调试流程使用 userId + department 请求参数。
 * Controller 只负责接收 HTTP 参数，真正的权限判断放在 DocumentService / KnowledgeBaseService。
 */
@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 上传受支持的学习资料并触发入库。
     *
     * Controller 只负责接收 multipart/form-data：
     * - knowledgeBaseId 来自 URL；
     * - 当前用户默认来自 JWT，userId + department 只用于 legacy 兼容；
     * - file 支持 PDF、Markdown、DOCX 和 TXT。
     *
     * 权限校验、重复检测、调用 FastAPI 都在 DocumentService 中完成。
     */
    @PostMapping
    public ApiResponse<DocumentResponse> indexDocument(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID knowledgeBaseId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(DocumentResponse.from(
                documentService.indexDocument(
                        knowledgeBaseId,
                        userIdOrRequestParam(currentUser, userId),
                        departmentOrRequestParam(currentUser, department),
                        file
                )
        ));
    }

    /**
     * 查询某个知识库下的文档列表。
     *
     * 查询前也要做权限校验，否则无权限用户虽然不能上传，
     * 但仍可能通过列表接口看到文件名、状态、FastAPI documentId 等信息。
     */
    @GetMapping
    public ApiResponse<List<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID knowledgeBaseId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String department
    ) {
        List<DocumentResponse> responses = documentService
                .listByKnowledgeBase(
                        knowledgeBaseId,
                        userIdOrRequestParam(currentUser, userId),
                        departmentOrRequestParam(currentUser, department)
                )
                .stream()
                .map(DocumentResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }
}
