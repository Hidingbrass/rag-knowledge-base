package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.document.DocumentResponse;
import com.example.aikb.service.DocumentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 文档管理接口。
 *
 * 文档上传链路：
 * 前端/Swagger 上传 PDF
 * -> Spring Boot 接收 MultipartFile
 * -> DocumentService 记录状态
 * -> FastApiRagClient 转发给 FastAPI /documents/index
 * -> FastAPI 完成解析、切分、Embedding、Qdrant 入库
 * -> Spring Boot 保存 fastApiDocumentId 和 AVAILABLE 状态
 */
@RestController
@RequestMapping("/api/knowledge-bases/{knowledgeBaseId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping
    public ApiResponse<DocumentResponse> indexDocument(
            @PathVariable UUID knowledgeBaseId,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.ok(DocumentResponse.from(
                documentService.indexDocument(knowledgeBaseId, file)
        ));
    }

    @GetMapping
    public ApiResponse<List<DocumentResponse>> listDocuments(@PathVariable UUID knowledgeBaseId) {
        List<DocumentResponse> responses = documentService.listByKnowledgeBase(knowledgeBaseId)
                .stream()
                .map(DocumentResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }
}
