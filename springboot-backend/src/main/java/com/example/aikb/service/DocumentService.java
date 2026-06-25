package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiDocumentIndexResponse;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.repository.InMemoryDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 文档业务服务。
 *
 * 这个类是 Day 2 的核心：
 * Spring Boot 不直接做 PDF 解析和向量入库，而是负责业务编排：
 * 1. 确认知识库存在；
 * 2. 创建本地文档记录；
 * 3. 调用 FastAPI /documents/index；
 * 4. 根据调用结果更新文档状态。
 */
@Service
public class DocumentService {

    private final KnowledgeBaseService knowledgeBaseService;
    private final InMemoryDocumentRepository documentRepository;
    private final FastApiRagClient fastApiRagClient;

    public DocumentService(
            KnowledgeBaseService knowledgeBaseService,
            InMemoryDocumentRepository documentRepository,
            FastApiRagClient fastApiRagClient
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentRepository = documentRepository;
        this.fastApiRagClient = fastApiRagClient;
    }

    public KnowledgeDocument indexDocument(UUID knowledgeBaseId, MultipartFile file) {
        knowledgeBaseService.getRequired(knowledgeBaseId);
        validatePdf(file);

        Instant now = Instant.now();
        KnowledgeDocument processingDocument = new KnowledgeDocument(
                UUID.randomUUID(),
                knowledgeBaseId,
                null,
                file.getOriginalFilename(),
                null,
                DocumentStatus.PROCESSING,
                0,
                null,
                now,
                now
        );
        documentRepository.save(processingDocument);

        try {
            FastApiDocumentIndexResponse fastApiResponse = fastApiRagClient.indexDocument(file);
            KnowledgeDocument availableDocument = new KnowledgeDocument(
                    processingDocument.id(),
                    processingDocument.knowledgeBaseId(),
                    fastApiResponse.documentId(),
                    fastApiResponse.filename(),
                    fastApiResponse.fileHash(),
                    DocumentStatus.AVAILABLE,
                    fastApiResponse.chunkCount(),
                    null,
                    processingDocument.createdAt(),
                    Instant.now()
            );

            return documentRepository.save(availableDocument);
        } catch (RuntimeException exception) {
            KnowledgeDocument failedDocument = new KnowledgeDocument(
                    processingDocument.id(),
                    processingDocument.knowledgeBaseId(),
                    null,
                    processingDocument.filename(),
                    null,
                    DocumentStatus.FAILED,
                    0,
                    exception.getMessage(),
                    processingDocument.createdAt(),
                    Instant.now()
            );
            documentRepository.save(failedDocument);
            throw exception;
        }
    }

    public List<KnowledgeDocument> listByKnowledgeBase(UUID knowledgeBaseId) {
        knowledgeBaseService.getRequired(knowledgeBaseId);
        return documentRepository.findByKnowledgeBaseId(knowledgeBaseId);
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        boolean filenameLooksLikePdf = filename != null && filename.toLowerCase().endsWith(".pdf");
        boolean contentTypeLooksLikePdf = "application/pdf".equalsIgnoreCase(file.getContentType());

        if (!filenameLooksLikePdf && !contentTypeLooksLikePdf) {
            throw new BusinessException("目前只支持 PDF 文件");
        }
    }
}
