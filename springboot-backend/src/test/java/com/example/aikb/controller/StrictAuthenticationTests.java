package com.example.aikb.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "auth.security.allow-legacy-identity-parameters=false")
@AutoConfigureMockMvc
@Transactional
class StrictAuthenticationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthAndAuthenticationEntryPointsShouldRemainPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "strict-public-user",
                                  "password": "secret123",
                                  "displayName": "Strict Public User",
                                  "department": "研发部"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void businessApiShouldRejectAnonymousIdentityParameters() throws Exception {
        mockMvc.perform(get("/api/chat/sessions")
                        .param("userId", "forged-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void businessApiShouldAcceptValidJwt() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "strict-jwt-user",
                                  "password": "secret123",
                                  "displayName": "Strict JWT User",
                                  "department": "研发部"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        String accessToken = root.path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/chat/sessions")
                        .param("userId", "forged-user")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void createKnowledgeBaseShouldFallbackToProfileDirectionWhenRequestOmitsIt() throws Exception {
        String accessToken = registerUser("strict-kb-default-direction", "Default Direction", "Java 后端");

        mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Default Direction Base",
                                  "description": "Use the profile fallback"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value("strict-kb-default-direction"))
                .andExpect(jsonPath("$.data.department").value("Java 后端"));
    }

    @Test
    void sameLearningDirectionJwtUserShouldNotAccessAnotherUsersKnowledgeBase() throws Exception {
        String ownerToken = registerUser("strict-kb-owner", "Strict KB Owner", "Java 后端");
        String peerToken = registerUser("strict-kb-peer", "Strict KB Peer", "Java 后端");

        String createResponse = mockMvc.perform(post("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Owner Private Study Base",
                                  "description": "Owner-only regression fixture",
                                  "department": "AI 应用开发"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ownerId").value("strict-kb-owner"))
                .andExpect(jsonPath("$.data.department").value("AI 应用开发"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String knowledgeBaseId = objectMapper.readTree(createResponse).path("data").path("id").asText();

        mockMvc.perform(get("/api/knowledge-bases")
                        .header("Authorization", "Bearer " + peerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(get("/api/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header("Authorization", "Bearer " + peerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("无权访问知识库: " + knowledgeBaseId));

        mockMvc.perform(get("/api/knowledge-bases/{knowledgeBaseId}/documents", knowledgeBaseId)
                        .header("Authorization", "Bearer " + peerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/chat/sessions")
                        .header("Authorization", "Bearer " + peerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "knowledgeBaseId": "%s",
                                  "title": "Forbidden peer session"
                                }
                                """.formatted(knowledgeBaseId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header("Authorization", "Bearer " + peerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Forbidden Update",
                                  "description": "must not change",
                                  "department": "Java 后端"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header("Authorization", "Bearer " + peerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Owner Study Base",
                                  "description": "owner may update",
                                  "department": "AI 应用开发"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Owner Study Base"));

        mockMvc.perform(delete("/api/knowledge-bases/{knowledgeBaseId}", knowledgeBaseId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
    }

    private String registerUser(String username, String displayName, String department) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "secret123",
                                  "displayName": "%s",
                                  "department": "%s"
                                }
                                """.formatted(username, displayName, department)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
