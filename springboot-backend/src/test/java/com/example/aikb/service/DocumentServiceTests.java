package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiDocumentIndexResponse;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * DocumentService 集成测试。
 * <p>
 * Spring Boot 侧使用 H2 + JPA 保存文档业务记录。
 * FastAPI 客户端使用 mock，避免测试依赖真实网络、模型服务和 Qdrant。
 */
@SpringBootTest
@Transactional
class DocumentServiceTests {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Autowired
    private DocumentService documentService;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    @Test
    void indexDocumentShouldBecomeAvailableWhenFastApiSucceeds() {
        KnowledgeBase knowledgeBase = createKnowledgeBase();
        MockMultipartFile file = pdfFile("demo.pdf", "fake pdf bytes");

        when(fastApiRagClient.indexDocument(any())).thenReturn(new FastApiDocumentIndexResponse(
                "fastapi-doc-1",
                "demo.pdf",
                12,
                "rag_chunks",
                "ignored-fastapi-hash"
        ));

        DocumentIndexResult result = documentService.indexDocument(knowledgeBase.id(), "user-1", "研发部", file);
        KnowledgeDocument document = result.document();

        assertThat(result.duplicated()).isFalse();
        assertThat(document.status()).isEqualTo(DocumentStatus.AVAILABLE);
        assertThat(document.fastApiDocumentId()).isEqualTo("fastapi-doc-1");
        assertThat(document.chunkCount()).isEqualTo(12);
        assertThat(document.fileHash()).isEqualTo(sha256("fake pdf bytes"));
    }

    @Test
    void indexDocumentShouldReturnExistingDocumentWhenFileHashDuplicated() {
        KnowledgeBase knowledgeBase = createKnowledgeBase();

        when(fastApiRagClient.indexDocument(any())).thenReturn(new FastApiDocumentIndexResponse(
                "fastapi-doc-1",
                "demo.pdf",
                12,
                "rag_chunks",
                "ignored-fastapi-hash"
        ));

        DocumentIndexResult firstResult = documentService.indexDocument(
                knowledgeBase.id(),
                "user-1",
                "研发部",
                pdfFile("demo.pdf", "same pdf bytes")
        );
        DocumentIndexResult secondResult = documentService.indexDocument(
                knowledgeBase.id(),
                "user-1",
                "研发部",
                pdfFile("demo-copy.pdf", "same pdf bytes")
        );
        KnowledgeDocument firstDocument = firstResult.document();
        KnowledgeDocument secondDocument = secondResult.document();

        List<KnowledgeDocument> documents = documentService.listByKnowledgeBase(knowledgeBase.id(), "user-1",
                "研发部");

        assertThat(firstResult.duplicated()).isFalse();
        assertThat(secondResult.duplicated()).isTrue();
        assertThat(secondDocument.id()).isEqualTo(firstDocument.id());
        assertThat(secondDocument.fastApiDocumentId()).isEqualTo("fastapi-doc-1");
        assertThat(documents).hasSize(1);
        verify(fastApiRagClient, times(1)).indexDocument(any());
    }

    @Test
    void indexDocumentShouldAcceptMarkdownDocxAndTxt() {
        KnowledgeBase knowledgeBase = createKnowledgeBase();
        when(fastApiRagClient.indexDocument(any())).thenAnswer(invocation -> {
            MockMultipartFile file = invocation.getArgument(0);
            return new FastApiDocumentIndexResponse(
                    "fastapi-" + file.getOriginalFilename(),
                    file.getOriginalFilename(),
                    2,
                    "rag_chunks",
                    "ignored-fastapi-hash"
            );
        });

        List<MockMultipartFile> files = List.of(
                documentFile("java.md", "text/markdown", "# Java"),
                documentFile("rag.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "fake docx"),
                documentFile("notes.txt", "text/plain", "plain notes")
        );

        for (MockMultipartFile file : files) {
            DocumentIndexResult result = documentService.indexDocument(
                    knowledgeBase.id(), "user-1", "研发部", file
            );
            assertThat(result.document().status()).isEqualTo(DocumentStatus.AVAILABLE);
            assertThat(result.document().filename()).isEqualTo(file.getOriginalFilename());
        }

        verify(fastApiRagClient, times(3)).indexDocument(any());
    }

    @Test
    void indexDocumentShouldRejectUnsupportedExtension() {
        KnowledgeBase knowledgeBase = createKnowledgeBase();

        assertThatThrownBy(() -> documentService.indexDocument(
                knowledgeBase.id(),
                "user-1",
                "研发部",
                documentFile("scores.csv", "text/csv", "name,value")
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支持 PDF、Markdown、Word（DOCX）和 TXT 文件");
    }

    @Test
    void deleteDocumentShouldRecheckOwnershipAndDeleteVectorBeforeBusinessRecord() {
        KnowledgeBase knowledgeBase = createKnowledgeBase();
        when(fastApiRagClient.indexDocument(any())).thenReturn(new FastApiDocumentIndexResponse(
                "delete-doc", "delete.md", 1, "rag_chunks", "ignored"
        ));
        documentService.indexDocument(
                knowledgeBase.id(), "user-1", "研发部",
                documentFile("delete.md", "text/markdown", "delete test")
        );

        String filename = documentService.deleteDocument(
                knowledgeBase.id(), "user-1", "研发部", "delete-doc"
        );

        assertThat(filename).isEqualTo("delete.md");
        assertThat(documentService.listByKnowledgeBase(
                knowledgeBase.id(), "user-1", "研发部"
        )).isEmpty();
        verify(fastApiRagClient).deleteDocument("delete-doc");
    }

    @Test
    void deleteDocumentShouldRejectNonOwnerBeforeVectorDeletion() {
        KnowledgeBase knowledgeBase = createKnowledgeBase();

        assertThatThrownBy(() -> documentService.deleteDocument(
                knowledgeBase.id(), "user-2", "研发部", "missing"
        )).hasMessageContaining("无权访问知识库");
        verify(fastApiRagClient, never()).deleteDocument(any());
    }

    private KnowledgeBase createKnowledgeBase() {
        return knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "测试知识库",
                "用于测试文档入库",
                "user-1",
                "研发部"
        ));
    }

    private MockMultipartFile pdfFile(String filename, String content) {
        return documentFile(filename, "application/pdf", content);
    }

    private MockMultipartFile documentFile(String filename, String contentType, String content) {
        return new MockMultipartFile("file", filename, contentType, content.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
