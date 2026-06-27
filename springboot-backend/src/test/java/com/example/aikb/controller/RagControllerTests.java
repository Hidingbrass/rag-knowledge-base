package com.example.aikb.controller;

import com.example.aikb.client.FastApiRagClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 旧版 RAG 直连接口测试。
 *
 * 项目现在必须通过聊天会话接口提问，否则会绕过知识库权限和文档归属校验。
 * 这个测试用于防止 /api/rag/ask 被误改回直接调用 FastAPI。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RagControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    @Test
    void legacyRagAskShouldBeDisabledAndShouldNotCallFastApi() throws Exception {
        mockMvc.perform(post("/api/rag/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "What is RAG?",
                                  "documentId": "fastapi-doc-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("旧版 /api/rag/ask 已停用，请使用 /api/chat/sessions/{sessionId}/ask"));

        verify(fastApiRagClient, never()).askWithRerank(anyString(), anyString());
    }
}
