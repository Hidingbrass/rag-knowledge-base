package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiDocumentIndexResponse;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 文档业务服务。
 * <p>
 * Spring Boot 不直接做文档解析、切分和向量入库，而是负责编排业务流程：
 * 1. 校验当前用户能访问目标知识库；
 * 2. 校验上传文件是不是支持的学习资料格式；
 * 3. 计算文件 SHA-256，用于重复上传检测；
 * 4. 没有重复时，创建本地 PROCESSING 文档记录；
 * 5. 调用 FastAPI /documents/index；
 * 6. 根据 FastAPI 调用结果，把文档状态更新为 AVAILABLE 或 FAILED。
 */
@Service
public class DocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".pdf", ".md", ".markdown", ".docx", ".txt"
    );

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentRepository documentRepository;
    private final FastApiRagClient fastApiRagClient;
    private final AiRateLimitService aiRateLimitService;
    private final UploadDuplicateLockService uploadDuplicateLockService;

    public DocumentService(
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeDocumentRepository documentRepository,
            FastApiRagClient fastApiRagClient,
            AiRateLimitService aiRateLimitService,
            UploadDuplicateLockService uploadDuplicateLockService
    ) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.documentRepository = documentRepository;
        this.fastApiRagClient = fastApiRagClient;
        this.aiRateLimitService = aiRateLimitService;
        this.uploadDuplicateLockService = uploadDuplicateLockService;
    }

    /**
     * 上传学习资料并触发 FastAPI 入库。
     * <p>
     * 重要边界：
     * - 权限判断和重复检测在 Spring Boot 做，因为它们依赖 MySQL 业务数据；
     * - 文档解析、切分、Embedding 和 Qdrant 写入在 FastAPI 做，因为它们属于 AI 能力；
     * - 入库失败时保留 FAILED 记录，方便前端展示失败原因，也方便后续排查。
     */
    public DocumentIndexResult indexDocument(UUID knowledgeBaseId,
                                             String userId,
                                             String department,
                                             MultipartFile file) {
        knowledgeBaseService.getRequiredWithAccess(knowledgeBaseId, userId, department);
        validateDocument(file);

        String fileHash = calculateSha256(file);
        KnowledgeDocument duplicatedDocument = findDuplicatedDocument(knowledgeBaseId, fileHash);
        if (duplicatedDocument != null) {
            return new DocumentIndexResult(duplicatedDocument, true);
        }

        boolean lockAcquired = uploadDuplicateLockService.tryLockDocumentUpload(userId, knowledgeBaseId, fileHash);
        if (!lockAcquired) {
            throw new BusinessException("该文档正在处理中，请稍后刷新文档列表");
        }

        aiRateLimitService.checkAiCallAllowed(userId, "DOCUMENT_INDEX");

        Instant now = Instant.now();
        KnowledgeDocument processingDocument = new KnowledgeDocument(
                UUID.randomUUID(),
                knowledgeBaseId,
                null,
                file.getOriginalFilename(),
                fileHash,
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
                    fileHash,
                    DocumentStatus.AVAILABLE,
                    fastApiResponse.chunkCount(),
                    null,
                    processingDocument.createdAt(),
                    Instant.now()
            );

            return new DocumentIndexResult(documentRepository.save(availableDocument), false);
        } catch (RuntimeException exception) {
            KnowledgeDocument failedDocument = new KnowledgeDocument(
                    processingDocument.id(),
                    processingDocument.knowledgeBaseId(),
                    null,
                    processingDocument.filename(),
                    fileHash,
                    DocumentStatus.FAILED,
                    0,
                    exception.getMessage(),
                    processingDocument.createdAt(),
                    Instant.now()
            );
            documentRepository.save(failedDocument);
            throw exception;
        } finally {
            uploadDuplicateLockService.unlockDocumentUpload(userId, knowledgeBaseId, fileHash);
        }
    }

    public List<KnowledgeDocument> listByKnowledgeBase(UUID knowledgeBaseId,
                                                       String userId,
                                                       String department) {
        knowledgeBaseService.getRequiredWithAccess(knowledgeBaseId, userId, department);
        return documentRepository.findByKnowledgeBaseIdOrderByCreatedAtDesc(knowledgeBaseId);
    }

    /**
     * 在同一个知识库内查找重复资料。
     * <p>
     * 只把 PROCESSING / AVAILABLE 当作可复用文档：
     * - PROCESSING：避免同一个文件被并发重复提交；
     * - AVAILABLE：已经成功入库，可以直接复用；
     * - FAILED：失败记录不复用，允许用户修复问题后重新上传。
     */
    private KnowledgeDocument findDuplicatedDocument(UUID knowledgeBaseId, String fileHash) {
        return documentRepository
                .findFirstByKnowledgeBaseIdAndFileHashAndStatusInOrderByCreatedAtDesc(
                        knowledgeBaseId,
                        fileHash,
                        List.of(DocumentStatus.PROCESSING, DocumentStatus.AVAILABLE)
                )
                .orElse(null);
    }

    /**
     * 计算文件内容 SHA-256。
     *
     * 用内容 hash 判断重复，比只看文件名可靠：
     * 同一份资料改名后上传，仍然能被识别为重复文件。
     */
    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(file.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new BusinessException("当前运行环境不支持 SHA-256", exception);
        }
    }

    /**
     * 基于文件名扩展名做上传白名单校验。
     * 实际内容能否解析由 FastAPI 再做一次严格校验，避免把解析能力重复放在业务层。
     */
    private void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String filename = file.getOriginalFilename();
        String normalizedFilename = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        boolean supported = SUPPORTED_EXTENSIONS.stream().anyMatch(normalizedFilename::endsWith);
        if (!supported) {
            throw new BusinessException("支持 PDF、Markdown、Word（DOCX）和 TXT 文件");
        }
    }
}
