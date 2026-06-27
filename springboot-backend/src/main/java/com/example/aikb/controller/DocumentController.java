package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.document.DocumentResponse;
import com.example.aikb.service.DocumentService;
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

/**
 * 文档管理接口。
 *
 * 当前学习版用 userId + department 请求参数模拟登录用户。
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
     * 上传 PDF 并触发入库。
     *
     * Controller 只负责接收 multipart/form-data：
     * - knowledgeBaseId 来自 URL；
     * - userId + department 模拟当前登录用户；
     * - file 是真正的 PDF 二进制文件。
     *
     * 权限校验、重复检测、调用 FastAPI 都在 DocumentService 中完成。
     */
    @PostMapping
    public ApiResponse<DocumentResponse> indexDocument(
            @PathVariable UUID knowledgeBaseId,
            @RequestParam String userId,
            @RequestParam String department,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(DocumentResponse.from(
                documentService.indexDocument(knowledgeBaseId, userId, department, file)
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
            @PathVariable UUID knowledgeBaseId,
            @RequestParam String userId,
            @RequestParam String department
    ) {
        List<DocumentResponse> responses = documentService
                .listByKnowledgeBase(knowledgeBaseId, userId, department)
                .stream()
                .map(DocumentResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }
}
