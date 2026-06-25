package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiDocumentIndexResponse;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.entity.KnowledgeDocument;
import com.example.aikb.enums.DocumentStatus;
import com.example.aikb.repository.InMemoryDocumentRepository;
import com.example.aikb.repository.InMemoryKnowledgeBaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DocumentService 单元测试。
 *
 * 这里用 mock 的 FastApiRagClient 代替真实 FastAPI 服务。
 * 好处是测试不会受网络、模型服务、Qdrant 状态影响，只验证 Spring Boot 自己的业务编排。
 */
class DocumentServiceTests {

    @Test
    void indexDocumentShouldBecomeAvailableWhenFastApiSucceeds() {
        KnowledgeBaseService knowledgeBaseService = new KnowledgeBaseService(
                new InMemoryKnowledgeBaseRepository()
        );
        KnowledgeBase knowledgeBase = knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "测试知识库",
                "用于测试文档入库",
                "user-1",
                "研发部"
        ));

        FastApiRagClient fastApiRagClient = mock(FastApiRagClient.class);
        DocumentService documentService = new DocumentService(
                knowledgeBaseService,
                new InMemoryDocumentRepository(),
                fastApiRagClient
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "demo.pdf",
                "application/pdf",
                "fake pdf bytes".getBytes()
        );
        when(fastApiRagClient.indexDocument(file)).thenReturn(new FastApiDocumentIndexResponse(
                "fastapi-doc-1",
                "demo.pdf",
                12,
                "rag_chunks",
                "hash-1"
        ));

        KnowledgeDocument document = documentService.indexDocument(knowledgeBase.id(), file);

        assertThat(document.status()).isEqualTo(DocumentStatus.AVAILABLE);
        assertThat(document.fastApiDocumentId()).isEqualTo("fastapi-doc-1");
        assertThat(document.chunkCount()).isEqualTo(12);
        assertThat(document.fileHash()).isEqualTo("hash-1");
    }
}
