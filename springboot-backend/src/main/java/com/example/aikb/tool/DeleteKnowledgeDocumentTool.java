package com.example.aikb.tool;

import com.example.aikb.enums.ToolOperation;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.service.DocumentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** 仅删除已在服务端确认参数中绑定的当前知识库文档。 */
@Component
public class DeleteKnowledgeDocumentTool implements AuthorizedTool {
    public static final String NAME = "delete_knowledge_document";

    private final DocumentService documentService;

    public DeleteKnowledgeDocumentTool(DocumentService documentService) {
        this.documentService = documentService;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public ToolOperation operation() { return ToolOperation.WRITE; }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        String documentId = arguments.path("document_id").asText("").strip();
        String knowledgeBaseId = arguments.path("knowledge_base_id").asText("").strip();
        if (documentId.isBlank() || knowledgeBaseId.isBlank()) {
            throw new BusinessException("删除工具缺少服务端绑定的文档参数");
        }
        String filename = documentService.deleteDocument(
                UUID.fromString(knowledgeBaseId),
                context.userId(),
                context.department(),
                documentId
        );
        return new ToolExecutionResult(
                "已删除文档“" + filename + "”及其向量数据。",
                Map.of("document_id", documentId, "filename", filename)
        );
    }
}
