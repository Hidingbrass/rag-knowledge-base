package com.example.aikb.controller;

import com.example.aikb.entity.AppUser;
import com.example.aikb.repository.AppUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        appUserRepository.deleteAll();
    }

    @Test
    void registerShouldCreateUserAndReturnJwt() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "DemoUser",
                                  "password": "secret123",
                                  "displayName": "Demo User",
                                  "department": "研发部"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("demouser"))
                .andExpect(jsonPath("$.data.user.department").value("研发部"));

        List<AppUser> users = appUserRepository.findAll();
        assertThat(users).hasSize(1);
        assertThat(users.get(0).username()).isEqualTo("demouser");
        assertThat(users.get(0).passwordHash()).isNotEqualTo("secret123");
        assertThat(users.get(0).passwordHash()).startsWith("$2");
    }

    @Test
    void registerShouldRejectDuplicatedUsername() throws Exception {
        registerDemoUser();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo-user",
                                  "password": "secret123",
                                  "displayName": "Demo User",
                                  "department": "研发部"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void loginShouldReturnJwtWhenPasswordMatches() throws Exception {
        registerDemoUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo-user",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("demo-user"));
    }

    @Test
    void loginShouldRejectWrongPassword() throws Exception {
        registerDemoUser();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo-user",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    void meShouldRequireJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    void meShouldReturnCurrentUserWhenJwtIsValid() throws Exception {
        String token = registerDemoUser();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("demo-user"))
                .andExpect(jsonPath("$.data.displayName").value("Demo User"))
                .andExpect(jsonPath("$.data.department").value("研发部"))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void updateProfileShouldPersistFieldsAndIssueRefreshedJwt() throws Exception {
        String token = registerDemoUser();

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "新的昵称",
                                  "department": "AI 应用开发"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("demo-user"))
                .andExpect(jsonPath("$.data.user.displayName").value("新的昵称"))
                .andExpect(jsonPath("$.data.user.department").value("AI 应用开发"));

        AppUser updated = appUserRepository.findByUsername("demo-user").orElseThrow();
        assertThat(updated.displayName()).isEqualTo("新的昵称");
        assertThat(updated.department()).isEqualTo("AI 应用开发");
    }

    @Test
    void updateProfileShouldRejectBlankFields() throws Exception {
        String token = registerDemoUser();

        mockMvc.perform(patch("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "", "department": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    private String registerDemoUser() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "demo-user",
                                  "password": "secret123",
                                  "displayName": "Demo User",
                                  "department": "研发部"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }
}
