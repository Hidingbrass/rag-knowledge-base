package com.example.aikb.controller;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.entity.KnowledgeBase;
import com.example.aikb.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DocumentController HTTP 层测试。
 *
 * Service 测试只能证明“会抛权限异常”；
 * Controller 测试可以继续证明全局异常处理器会把权限异常转换成 HTTP 403。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    private KnowledgeBase createDevKnowledgeBase() {
        return knowledgeBaseService.create(new CreateKnowledgeBaseRequest(
                "Research Knowledge Base",
                "Used by controller permission tests",
                "user-1",
                "dev"
        ));
    }

    @Test
    void listDocumentsShouldReturnForbiddenWhenUserHasNoAccess() throws Exception {
        KnowledgeBase knowledgeBase = createDevKnowledgeBase();

        mockMvc.perform(get("/api/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBase.id())
                        .param("userId", "user-3")
                        .param("department", "qa"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问知识库: " + knowledgeBase.id()));
    }

    @Test
    void uploadDocumentShouldReturnForbiddenBeforeCallingFastApiWhenUserHasNoAccess() throws Exception {
        KnowledgeBase knowledgeBase = createDevKnowledgeBase();

        mockMvc.perform(multipart("/api/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBase.id())
                        .file("file", "fake pdf bytes".getBytes())
                        .param("userId", "user-3")
                        .param("department", "qa")
                        .contentType("application/pdf"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问知识库: " + knowledgeBase.id()));

        verify(fastApiRagClient, never()).indexDocument(any());
    }
}
