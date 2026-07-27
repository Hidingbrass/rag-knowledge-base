package com.example.aikb.controller;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiInterviewPrepResponse;
import com.example.aikb.dto.fastapi.FastApiInterviewQuestionAnswer;
import com.example.aikb.dto.fastapi.FastApiJdParseResponse;
import com.example.aikb.dto.fastapi.FastApiJobAttachmentTextResponse;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.fastapi.FastApiJobDeliveryPackageResponse;
import com.example.aikb.dto.fastapi.FastApiProjectTalkingPoint;
import com.example.aikb.dto.fastapi.FastApiResumeOptimizeResponse;
import com.example.aikb.dto.fastapi.FastApiResumeParseResponse;
import com.example.aikb.dto.fastapi.FastApiResumeProject;
import com.example.aikb.dto.fastapi.FastApiResumeRewriteSuggestion;
import com.example.aikb.dto.fastapi.FastApiStarInterviewAnswerResponse;
import com.example.aikb.entity.JobAnalysisTask;
import com.example.aikb.entity.JobFavorite;
import com.example.aikb.entity.JobGeneratedTask;
import com.example.aikb.entity.JobGeneratedTaskType;
import com.example.aikb.entity.JobResumeVersion;
import com.example.aikb.repository.AppUserRepository;
import com.example.aikb.repository.JobAnalysisTaskRepository;
import com.example.aikb.repository.JobFavoriteRepository;
import com.example.aikb.repository.JobGeneratedTaskRepository;
import com.example.aikb.repository.JobResumeVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 求职辅助 Agent Spring Boot 入口测试。
 *
 * 这里不调用真实 FastAPI，也不调用真实大模型；
 * 只验证 Controller -> Service -> FastApiRagClient 的 Spring Boot 链路是否正确。
 */
@SpringBootTest
@AutoConfigureMockMvc
class JobAgentControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FastApiRagClient fastApiRagClient;

    @Autowired
    private JobAnalysisTaskRepository jobAnalysisTaskRepository;

    @Autowired
    private JobFavoriteRepository jobFavoriteRepository;

    @Autowired
    private JobGeneratedTaskRepository jobGeneratedTaskRepository;

    @Autowired
    private JobResumeVersionRepository jobResumeVersionRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jobAnalysisTaskRepository.deleteAll();
        jobFavoriteRepository.deleteAll();
        jobGeneratedTaskRepository.deleteAll();
        jobResumeVersionRepository.deleteAll();
        appUserRepository.deleteAll();
    }

    @Test
    void analyzeShouldReturnJobAnalyzeResponse() throws Exception {
        when(fastApiRagClient.analyzeJob(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG"
        )).thenReturn(new FastApiJobAnalyzeResponse(
                95,
                List.of("Java", "Spring Boot", "FastAPI", "RAG"),
                List.of("Redis"),
                List.of("端到端项目经验完整"),
                List.of("缓存经验体现较少"),
                List.of("补充 Redis 使用场景"),
                List.of("Rerank 解决了什么问题？")
        ));

        mockMvc.perform(post("/api/job-agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、FastAPI、RAG"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.match_score").value(95))
                .andExpect(jsonPath("$.data.matched_skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.missing_skills[0]").value("Redis"))
                .andExpect(jsonPath("$.data.strengths[0]").value("端到端项目经验完整"))
                .andExpect(jsonPath("$.data.interview_questions[0]").value("Rerank 解决了什么问题？"));

        verify(fastApiRagClient).analyzeJob(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG"
        );

        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc("demo-user");
        assertThat(tasks).hasSize(1);

        JobAnalysisTask task = tasks.get(0);
        assertThat(task.userId()).isEqualTo("demo-user");
        assertThat(task.resumeText()).isEqualTo("Spring Boot FastAPI RAG 项目");
        assertThat(task.jobDescription()).isEqualTo("需要 Java、Spring Boot、FastAPI、RAG");
        assertThat(task.matchScore()).isEqualTo(95);
        assertThat(task.resultJson()).contains("Spring Boot");
    }

    @Test
    void analyzeShouldUseJwtUserWhenRequestUserIdIsMissing() throws Exception {
        String token = registerDemoUser();
        when(fastApiRagClient.analyzeJob(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG"
        )).thenReturn(new FastApiJobAnalyzeResponse(
                95,
                List.of("Java", "Spring Boot", "FastAPI", "RAG"),
                List.of("Redis"),
                List.of("端到端项目经验完整"),
                List.of("缓存经验体现较少"),
                List.of("补充 Redis 使用场景"),
                List.of("Rerank 解决了什么问题？")
        ));

        mockMvc.perform(post("/api/job-agent/analyze")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、FastAPI、RAG"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.match_score").value(95));

        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc("demo-user");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).userId()).isEqualTo("demo-user");
    }

    @Test
    void analyzeFromFileShouldExtractJdThenSaveTask() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "jd.png",
                "image/png",
                "fake-image-content".getBytes()
        );
        when(fastApiRagClient.extractJdText(any(MultipartFile.class))).thenReturn(new FastApiJobAttachmentTextResponse(
                "jd.png",
                "image_ocr",
                "岗位要求 Java、Spring Boot、Redis。",
                List.of()
        ));
        when(fastApiRagClient.analyzeJob(
                "我做过 Spring Boot RAG 项目",
                "岗位要求 Java、Spring Boot、Redis。"
        )).thenReturn(new FastApiJobAnalyzeResponse(
                92,
                List.of("Java", "Spring Boot"),
                List.of("Redis"),
                List.of("项目经验匹配"),
                List.of("Redis 体现不足"),
                List.of("补充 Redis 限流经验"),
                List.of("Redis 限流怎么做？")
        ));

        mockMvc.perform(multipart("/api/job-agent/analyze-from-file")
                        .file(file)
                        .param("userId", "demo-user")
                        .param("resumeText", "我做过 Spring Boot RAG 项目"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.match_score").value(92))
                .andExpect(jsonPath("$.data.missing_skills[0]").value("Redis"));

        verify(fastApiRagClient).extractJdText(any(MultipartFile.class));
        verify(fastApiRagClient).analyzeJob(
                "我做过 Spring Boot RAG 项目",
                "岗位要求 Java、Spring Boot、Redis。"
        );

        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc("demo-user");
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).jobDescription()).isEqualTo("岗位要求 Java、Spring Boot、Redis。");
        assertThat(tasks.get(0).matchScore()).isEqualTo(92);
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

    @Test
    void analyzeShouldReturnBadRequestWhenResumeTextIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "",
                                  "jobDescription": "需要 Java、Spring Boot、FastAPI、RAG"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("resumeText: 简历内容不能为空"));

        verify(fastApiRagClient, never()).analyzeJob(
                "",
                "需要 Java、Spring Boot、FastAPI、RAG"
        );
    }

    @Test
    void createFavoriteShouldSaveFavoriteJob() throws Exception {
        mockMvc.perform(post("/api/job-agent/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "jobTitle": "Java 后端开发工程师",
                                  "companyName": "AI 科技公司",
                                  "jobDescription": "需要 Java、Spring Boot、RAG 和向量数据库",
                                  "sourceUrl": "https://example.com/jobs/1",
                                  "notes": "重点准备 RAG 项目"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.userId").value("demo-user"))
                .andExpect(jsonPath("$.data.jobTitle").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.companyName").value("AI 科技公司"))
                .andExpect(jsonPath("$.data.jobDescription").value("需要 Java、Spring Boot、RAG 和向量数据库"))
                .andExpect(jsonPath("$.data.sourceUrl").value("https://example.com/jobs/1"))
                .andExpect(jsonPath("$.data.notes").value("重点准备 RAG 项目"));

        List<JobFavorite> favorites = jobFavoriteRepository.findByUserIdOrderByCreatedAtDesc("demo-user");
        assertThat(favorites).hasSize(1);
        assertThat(favorites.get(0).jobTitle()).isEqualTo("Java 后端开发工程师");
    }

    @Test
    void createFavoriteShouldReturnBadRequestWhenRequiredFieldsAreBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/favorites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "",
                                  "jobTitle": "Java 后端开发工程师",
                                  "companyName": "AI 科技公司",
                                  "jobDescription": "需要 Java"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));
    }

    @Test
    void listFavoritesShouldReturnOnlyCurrentUserFavorites() throws Exception {
        jobFavoriteRepository.save(new JobFavorite(
                UUID.randomUUID(),
                "demo-user",
                "Java 后端开发工程师",
                "AI 科技公司",
                "需要 Java、Spring Boot、RAG",
                "https://example.com/jobs/1",
                "重点准备 RAG",
                Instant.now()
        ));
        jobFavoriteRepository.save(new JobFavorite(
                UUID.randomUUID(),
                "other-user",
                "Python 后端开发",
                "Other 公司",
                "需要 Python",
                null,
                null,
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/favorites")
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value("demo-user"))
                .andExpect(jsonPath("$.data[0].jobTitle").value("Java 后端开发工程师"));
    }

    @Test
    void listFavoritesPageShouldReturnPagedFavoritesWithMetadata() throws Exception {
        Instant now = Instant.now();
        jobFavoriteRepository.save(new JobFavorite(
                UUID.randomUUID(),
                "demo-user",
                "较早岗位",
                "AI 科技公司",
                "需要 Java",
                null,
                null,
                now.minusSeconds(60)
        ));
        jobFavoriteRepository.save(new JobFavorite(
                UUID.randomUUID(),
                "demo-user",
                "最新岗位",
                "AI 科技公司",
                "需要 Spring Boot",
                null,
                null,
                now
        ));

        mockMvc.perform(get("/api/job-agent/favorites/page")
                        .param("userId", "demo-user")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].jobTitle").value("最新岗位"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getFavoriteShouldReturnDetailAndRejectOtherUser() throws Exception {
        UUID favoriteId = UUID.randomUUID();
        jobFavoriteRepository.save(new JobFavorite(
                favoriteId,
                "demo-user",
                "Java 后端开发工程师",
                "AI 科技公司",
                "需要 Java、Spring Boot、RAG",
                "https://example.com/jobs/1",
                "重点准备 RAG",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/favorites/{favoriteId}", favoriteId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(favoriteId.toString()))
                .andExpect(jsonPath("$.data.jobTitle").value("Java 后端开发工程师"));

        mockMvc.perform(get("/api/job-agent/favorites/{favoriteId}", favoriteId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问该收藏岗位: " + favoriteId));
    }

    @Test
    void deleteFavoriteShouldDeleteOwnedFavoriteOnly() throws Exception {
        UUID favoriteId = UUID.randomUUID();
        jobFavoriteRepository.save(new JobFavorite(
                favoriteId,
                "demo-user",
                "Java 后端开发工程师",
                "AI 科技公司",
                "需要 Java、Spring Boot、RAG",
                null,
                null,
                Instant.now()
        ));

        mockMvc.perform(delete("/api/job-agent/favorites/{favoriteId}", favoriteId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(jobFavoriteRepository.findById(favoriteId)).isPresent();

        mockMvc.perform(delete("/api/job-agent/favorites/{favoriteId}", favoriteId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(jobFavoriteRepository.findById(favoriteId)).isEmpty();
    }

    @Test
    void createResumeVersionShouldSaveResumeVersion() throws Exception {
        mockMvc.perform(post("/api/job-agent/resume-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "versionName": "RAG 项目强化版",
                                  "targetRole": "Java 后端开发工程师",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "notes": "突出 RAG 项目"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value("demo-user"))
                .andExpect(jsonPath("$.data.versionName").value("RAG 项目强化版"))
                .andExpect(jsonPath("$.data.targetRole").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.resumeText").value("Spring Boot FastAPI RAG 项目"))
                .andExpect(jsonPath("$.data.notes").value("突出 RAG 项目"));

        List<JobResumeVersion> versions = jobResumeVersionRepository.findByUserIdOrderByUpdatedAtDesc("demo-user");
        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).versionName()).isEqualTo("RAG 项目强化版");
    }

    @Test
    void createResumeVersionShouldReturnBadRequestWhenRequiredFieldsAreBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/resume-versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "",
                                  "versionName": "RAG 项目强化版",
                                  "targetRole": "Java 后端开发工程师",
                                  "resumeText": "Spring Boot FastAPI RAG 项目"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));
    }

    @Test
    void listResumeVersionsShouldReturnOnlyCurrentUserVersions() throws Exception {
        jobResumeVersionRepository.save(new JobResumeVersion(
                UUID.randomUUID(),
                "demo-user",
                "RAG 项目强化版",
                "Java 后端开发工程师",
                "Spring Boot FastAPI RAG 项目",
                "突出 RAG 项目",
                Instant.now(),
                Instant.now()
        ));
        jobResumeVersionRepository.save(new JobResumeVersion(
                UUID.randomUUID(),
                "other-user",
                "Python 版本",
                "Python 后端开发",
                "Python 项目",
                null,
                Instant.now(),
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/resume-versions")
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value("demo-user"))
                .andExpect(jsonPath("$.data[0].versionName").value("RAG 项目强化版"));
    }

    @Test
    void listResumeVersionsPageShouldReturnPagedVersionsWithMetadata() throws Exception {
        Instant now = Instant.now();
        jobResumeVersionRepository.save(new JobResumeVersion(
                UUID.randomUUID(),
                "demo-user",
                "较早版本",
                "Java 后端开发工程师",
                "Spring Boot FastAPI RAG 项目",
                null,
                now.minusSeconds(60),
                now.minusSeconds(60)
        ));
        jobResumeVersionRepository.save(new JobResumeVersion(
                UUID.randomUUID(),
                "demo-user",
                "最新版本",
                "AI 应用开发工程师",
                "Spring Boot FastAPI RAG 项目",
                null,
                now,
                now
        ));

        mockMvc.perform(get("/api/job-agent/resume-versions/page")
                        .param("userId", "demo-user")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].versionName").value("最新版本"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void getResumeVersionShouldReturnDetailAndRejectOtherUser() throws Exception {
        UUID versionId = UUID.randomUUID();
        jobResumeVersionRepository.save(new JobResumeVersion(
                versionId,
                "demo-user",
                "RAG 项目强化版",
                "Java 后端开发工程师",
                "Spring Boot FastAPI RAG 项目",
                "突出 RAG 项目",
                Instant.now(),
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/resume-versions/{versionId}", versionId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(versionId.toString()))
                .andExpect(jsonPath("$.data.versionName").value("RAG 项目强化版"));

        mockMvc.perform(get("/api/job-agent/resume-versions/{versionId}", versionId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问该简历版本: " + versionId));
    }

    @Test
    void updateResumeVersionShouldUpdateOwnedVersionOnly() throws Exception {
        UUID versionId = UUID.randomUUID();
        jobResumeVersionRepository.save(new JobResumeVersion(
                versionId,
                "demo-user",
                "RAG 项目强化版",
                "Java 后端开发工程师",
                "Spring Boot FastAPI RAG 项目",
                "突出 RAG 项目",
                Instant.now(),
                Instant.now()
        ));

        mockMvc.perform(put("/api/job-agent/resume-versions/{versionId}", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "other-user",
                                  "versionName": "非法覆盖",
                                  "targetRole": "Python 后端开发",
                                  "resumeText": "Python 项目",
                                  "notes": "不应成功"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(put("/api/job-agent/resume-versions/{versionId}", versionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "versionName": "AI 应用开发版",
                                  "targetRole": "AI 应用开发工程师",
                                  "resumeText": "Spring Boot FastAPI RAG 项目，突出 AI 应用落地。",
                                  "notes": "突出 AI 应用"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.versionName").value("AI 应用开发版"))
                .andExpect(jsonPath("$.data.targetRole").value("AI 应用开发工程师"))
                .andExpect(jsonPath("$.data.resumeText").value("Spring Boot FastAPI RAG 项目，突出 AI 应用落地。"));
    }

    @Test
    void deleteResumeVersionShouldDeleteOwnedVersionOnly() throws Exception {
        UUID versionId = UUID.randomUUID();
        jobResumeVersionRepository.save(new JobResumeVersion(
                versionId,
                "demo-user",
                "RAG 项目强化版",
                "Java 后端开发工程师",
                "Spring Boot FastAPI RAG 项目",
                null,
                Instant.now(),
                Instant.now()
        ));

        mockMvc.perform(delete("/api/job-agent/resume-versions/{versionId}", versionId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(jobResumeVersionRepository.findById(versionId)).isPresent();

        mockMvc.perform(delete("/api/job-agent/resume-versions/{versionId}", versionId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(jobResumeVersionRepository.findById(versionId)).isEmpty();
    }

    @Test
    void parseResumeShouldReturnResumeParseResponse() throws Exception {
        when(fastApiRagClient.parseResume(
                "Spring Boot FastAPI RAG 项目"
        )).thenReturn(new FastApiResumeParseResponse(
                List.of("Java 后端开发", "AI 应用开发"),
                List.of("Java", "Spring Boot", "FastAPI", "RAG"),
                List.of(new FastApiResumeProject(
                        "企业知识库 RAG 系统",
                        "后端开发",
                        List.of("Spring Boot", "FastAPI", "Qdrant"),
                        "实现文档入库、向量检索和问答链路。",
                        List.of("完成端到端 RAG 链路")
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of("具备端到端项目经验"),
                List.of("RAG", "向量数据库")
        ));

        mockMvc.perform(post("/api/job-agent/resume/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeText": "Spring Boot FastAPI RAG 项目"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.target_roles[0]").value("Java 后端开发"))
                .andExpect(jsonPath("$.data.skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.projects[0].name").value("企业知识库 RAG 系统"))
                .andExpect(jsonPath("$.data.projects[0].tech_stack[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.strengths[0]").value("具备端到端项目经验"))
                .andExpect(jsonPath("$.data.keywords[0]").value("RAG"));

        verify(fastApiRagClient).parseResume("Spring Boot FastAPI RAG 项目");
    }

    @Test
    void parseResumeShouldReturnBadRequestWhenResumeTextIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/resume/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeText": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("resumeText: 简历内容不能为空"));

        verify(fastApiRagClient, never()).parseResume("");
    }

    @Test
    void parseJdShouldReturnJdParseResponse() throws Exception {
        when(fastApiRagClient.parseJd(
                "岗位要求 Java、Spring Boot、MySQL，有 RAG 项目经验优先。"
        )).thenReturn(new FastApiJdParseResponse(
                "Java 后端开发工程师",
                "中级",
                List.of("Java", "Spring Boot", "MySQL"),
                List.of("RAG", "向量数据库"),
                List.of("负责后端接口开发"),
                List.of("熟悉 Java 技术栈"),
                List.of("Java", "Spring Boot", "RAG"),
                List.of("需要说明生产环境经验")
        ));

        mockMvc.perform(post("/api/job-agent/jd/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": "岗位要求 Java、Spring Boot、MySQL，有 RAG 项目经验优先。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.job_title").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.seniority").value("中级"))
                .andExpect(jsonPath("$.data.required_skills[0]").value("Java"))
                .andExpect(jsonPath("$.data.preferred_skills[0]").value("RAG"))
                .andExpect(jsonPath("$.data.keywords[2]").value("RAG"));

        verify(fastApiRagClient).parseJd("岗位要求 Java、Spring Boot、MySQL，有 RAG 项目经验优先。");
    }

    @Test
    void parseJdShouldReturnBadRequestWhenJobDescriptionIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/jd/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobDescription": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("jobDescription: 岗位 JD 不能为空"));

        verify(fastApiRagClient, never()).parseJd("");
    }

    @Test
    void optimizeResumeShouldReturnResumeOptimizeResponse() throws Exception {
        when(fastApiRagClient.optimizeResume(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和向量数据库"
        )).thenReturn(new FastApiResumeOptimizeResponse(
                "强化 RAG 项目和 Java 后端能力表达。",
                "Java 后端开发工程师",
                List.of("生产环境经验体现不足"),
                List.of(new FastApiResumeRewriteSuggestion(
                        "项目经历",
                        "项目成果表达不够贴近 JD",
                        "突出 Spring Boot、FastAPI、Qdrant 和大模型调用链路",
                        "我做过 RAG 项目。",
                        "基于 Spring Boot + FastAPI 构建企业知识库 RAG 系统。",
                        List.of("Spring Boot", "FastAPI", "RAG")
                )),
                List.of("Redis"),
                List.of("准备说明接口容错和部署方案")
        ));

        mockMvc.perform(post("/api/job-agent/resume/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、RAG 和向量数据库"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.summary").value("强化 RAG 项目和 Java 后端能力表达。"))
                .andExpect(jsonPath("$.data.target_position").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.gap_summary[0]").value("生产环境经验体现不足"))
                .andExpect(jsonPath("$.data.rewrite_suggestions[0].section").value("项目经历"))
                .andExpect(jsonPath("$.data.rewrite_suggestions[0].after_text").value("基于 Spring Boot + FastAPI 构建企业知识库 RAG 系统。"))
                .andExpect(jsonPath("$.data.rewrite_suggestions[0].keywords_added[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.missing_keywords[0]").value("Redis"))
                .andExpect(jsonPath("$.data.action_items[0]").value("准备说明接口容错和部署方案"));

        verify(fastApiRagClient).optimizeResume(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和向量数据库"
        );

        List<JobGeneratedTask> tasks = jobGeneratedTaskRepository.findByUserIdAndTaskTypeOrderByCreatedAtDesc(
                "demo-user",
                JobGeneratedTaskType.RESUME_OPTIMIZE
        );
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).resultJson()).contains("强化 RAG 项目和 Java 后端能力表达。");
    }

    @Test
    void optimizeResumeShouldReturnBadRequestWhenResumeOrJdIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/resume/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "",
                                  "jobDescription": "需要 Java、Spring Boot、RAG"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("resumeText: 简历内容不能为空"));

        mockMvc.perform(post("/api/job-agent/resume/optimize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("jobDescription: 岗位 JD 不能为空"));

        verify(fastApiRagClient, never()).optimizeResume("", "需要 Java、Spring Boot、RAG");
        verify(fastApiRagClient, never()).optimizeResume("Spring Boot FastAPI RAG 项目", "");
    }

    @Test
    void prepareInterviewShouldReturnInterviewPrepResponse() throws Exception {
        when(fastApiRagClient.prepareInterview(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和向量数据库"
        )).thenReturn(new FastApiInterviewPrepResponse(
                "Java 后端开发工程师",
                "面试官您好，我主要做 Java 后端和 AI 应用开发。",
                List.of(new FastApiProjectTalkingPoint(
                        "企业知识库 RAG 系统",
                        "我负责 Spring Boot 业务后端和 FastAPI AI 服务联调。",
                        List.of("Qdrant 向量检索", "Rerank 拒答阈值"),
                        List.of("为什么 MySQL 和 Qdrant 要分开？")
                )),
                List.of(new FastApiInterviewQuestionAnswer(
                        "RAG 如何减少幻觉？",
                        List.of("引用来源", "无依据拒答", "Rerank 重排")
                )),
                List.of(new FastApiInterviewQuestionAnswer(
                        "遇到接口联调问题怎么办？",
                        List.of("确认接口契约", "查看日志", "补充测试")
                )),
                List.of("团队当前 AI 应用的落地场景是什么？"),
                List.of("准备 RAG 架构讲解", "复习 Qdrant 与 MySQL 分工")
        ));

        mockMvc.perform(post("/api/job-agent/interview/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、RAG 和向量数据库"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.target_position").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.self_introduction").value("面试官您好，我主要做 Java 后端和 AI 应用开发。"))
                .andExpect(jsonPath("$.data.project_talking_points[0].project_name").value("企业知识库 RAG 系统"))
                .andExpect(jsonPath("$.data.project_talking_points[0].technical_depth[0]").value("Qdrant 向量检索"))
                .andExpect(jsonPath("$.data.technical_questions[0].question").value("RAG 如何减少幻觉？"))
                .andExpect(jsonPath("$.data.technical_questions[0].answer_points[0]").value("引用来源"))
                .andExpect(jsonPath("$.data.behavioral_questions[0].question").value("遇到接口联调问题怎么办？"))
                .andExpect(jsonPath("$.data.questions_to_ask[0]").value("团队当前 AI 应用的落地场景是什么？"))
                .andExpect(jsonPath("$.data.preparation_checklist[0]").value("准备 RAG 架构讲解"));

        verify(fastApiRagClient).prepareInterview(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和向量数据库"
        );

        List<JobGeneratedTask> tasks = jobGeneratedTaskRepository.findByUserIdAndTaskTypeOrderByCreatedAtDesc(
                "demo-user",
                JobGeneratedTaskType.INTERVIEW_PREP
        );
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).resultJson()).contains("Java 后端开发工程师");
    }

    @Test
    void prepareInterviewShouldReturnBadRequestWhenResumeOrJdIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/interview/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "",
                                  "jobDescription": "需要 Java、Spring Boot、RAG"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("resumeText: 简历内容不能为空"));

        mockMvc.perform(post("/api/job-agent/interview/prepare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("jobDescription: 岗位 JD 不能为空"));

        verify(fastApiRagClient, never()).prepareInterview("", "需要 Java、Spring Boot、RAG");
        verify(fastApiRagClient, never()).prepareInterview("Spring Boot FastAPI RAG 项目", "");
    }

    @Test
    void generateStarInterviewAnswerShouldReturnResponseAndSaveHistory() throws Exception {
        when(fastApiRagClient.generateStarInterviewAnswer(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和向量数据库",
                "RAG 中如何解决幻觉问题？"
        )).thenReturn(new FastApiStarInterviewAnswerResponse(
                "Java 后端开发工程师",
                "RAG 中如何解决幻觉问题？",
                "项目需要基于企业文档回答问题，并避免无依据回答。",
                "我负责让问答结果能基于检索片段生成，并在依据不足时拒答。",
                List.of("保留引用来源", "接入 Rerank", "设置拒答阈值"),
                "最终系统可以返回带来源的答案，并对低相关问题拒答。",
                "在我的 RAG 项目中，我主要从引用、重排和拒答三层控制幻觉。",
                List.of("RAG 工程实践", "效果控制", "可解释性"),
                List.of("拒答阈值如何确定？")
        ));

        mockMvc.perform(post("/api/job-agent/interview/star-answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、RAG 和向量数据库",
                                  "question": "RAG 中如何解决幻觉问题？"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.target_position").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.question").value("RAG 中如何解决幻觉问题？"))
                .andExpect(jsonPath("$.data.situation").value("项目需要基于企业文档回答问题，并避免无依据回答。"))
                .andExpect(jsonPath("$.data.task").value("我负责让问答结果能基于检索片段生成，并在依据不足时拒答。"))
                .andExpect(jsonPath("$.data.action[0]").value("保留引用来源"))
                .andExpect(jsonPath("$.data.result").value("最终系统可以返回带来源的答案，并对低相关问题拒答。"))
                .andExpect(jsonPath("$.data.answer").value("在我的 RAG 项目中，我主要从引用、重排和拒答三层控制幻觉。"))
                .andExpect(jsonPath("$.data.highlights[0]").value("RAG 工程实践"))
                .andExpect(jsonPath("$.data.follow_up_questions[0]").value("拒答阈值如何确定？"));

        verify(fastApiRagClient).generateStarInterviewAnswer(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和向量数据库",
                "RAG 中如何解决幻觉问题？"
        );

        List<JobGeneratedTask> tasks = jobGeneratedTaskRepository.findByUserIdAndTaskTypeOrderByCreatedAtDesc(
                "demo-user",
                JobGeneratedTaskType.STAR_INTERVIEW_ANSWER
        );
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).resultJson()).contains("RAG 工程实践");
    }

    @Test
    void generateStarInterviewAnswerShouldReturnBadRequestWhenQuestionIsBlank() throws Exception {
        mockMvc.perform(post("/api/job-agent/interview/star-answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、RAG",
                                  "question": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("question: 面试问题不能为空"));

        verify(fastApiRagClient, never()).generateStarInterviewAnswer(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                ""
        );
    }

    @Test
    void generateJobDeliveryPackageShouldReturnResponseAndSaveGeneratedTask() throws Exception {
        when(fastApiRagClient.generateJobDeliveryPackage(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和大模型应用"
        )).thenReturn(new FastApiJobDeliveryPackageResponse(
                "Java 后端开发工程师",
                "面试官您好，我主要做 Java 后端和 AI 应用落地。",
                "我重点介绍企业知识库 RAG 项目，它完成了文档入库、检索、重排和问答。",
                List.of("Spring Boot 负责业务权限和持久化", "FastAPI 负责 AI 服务编排"),
                List.of("高并发经验可以结合 Redis 限流和上传防重复提交说明"),
                "我希望继续把后端工程能力和 AI 应用落地结合起来。",
                List.of("练熟 RAG 全链路", "准备 Redis 限流细节")
        ));

        mockMvc.perform(post("/api/job-agent/delivery-package")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "resumeText": "Spring Boot FastAPI RAG 项目",
                                  "jobDescription": "需要 Java、Spring Boot、RAG 和大模型应用"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.target_position").value("Java 后端开发工程师"))
                .andExpect(jsonPath("$.data.project_pitch").value("我重点介绍企业知识库 RAG 项目，它完成了文档入库、检索、重排和问答。"))
                .andExpect(jsonPath("$.data.architecture_talking_points[0]").value("Spring Boot 负责业务权限和持久化"))
                .andExpect(jsonPath("$.data.rehearsal_checklist[0]").value("练熟 RAG 全链路"));

        verify(fastApiRagClient).generateJobDeliveryPackage(
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG 和大模型应用"
        );

        List<JobGeneratedTask> tasks = jobGeneratedTaskRepository.findByUserIdAndTaskTypeOrderByCreatedAtDesc(
                "demo-user",
                JobGeneratedTaskType.JOB_DELIVERY_PACKAGE
        );
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).resultJson()).contains("Redis 限流");
    }

    @Test
    void listGeneratedTasksShouldFilterByUserAndType() throws Exception {
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                "demo-user",
                JobGeneratedTaskType.RESUME_OPTIMIZE,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"summary\":\"优化建议\"}",
                Instant.now()
        ));
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                "demo-user",
                JobGeneratedTaskType.INTERVIEW_PREP,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"target_position\":\"Java 后端开发工程师\"}",
                Instant.now()
        ));
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                "other-user",
                JobGeneratedTaskType.RESUME_OPTIMIZE,
                "Python 项目",
                "需要 Python",
                "{\"summary\":\"其他用户\"}",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/generated-tasks")
                        .param("userId", "demo-user")
                        .param("taskType", "RESUME_OPTIMIZE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value("demo-user"))
                .andExpect(jsonPath("$.data[0].taskType").value("RESUME_OPTIMIZE"))
                .andExpect(jsonPath("$.data[0].resultJson").value("{\"summary\":\"优化建议\"}"));
    }

    @Test
    void listGeneratedTasksPageShouldFilterByUserAndTypeWithMetadata() throws Exception {
        Instant now = Instant.now();
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                "demo-user",
                JobGeneratedTaskType.RESUME_OPTIMIZE,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"summary\":\"较早优化建议\"}",
                now.minusSeconds(60)
        ));
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                "demo-user",
                JobGeneratedTaskType.RESUME_OPTIMIZE,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"summary\":\"最新优化建议\"}",
                now
        ));
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                "demo-user",
                JobGeneratedTaskType.INTERVIEW_PREP,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"target_position\":\"Java 后端开发工程师\"}",
                now.plusSeconds(60)
        ));

        mockMvc.perform(get("/api/job-agent/generated-tasks/page")
                        .param("userId", "demo-user")
                        .param("taskType", "RESUME_OPTIMIZE")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].resultJson").value("{\"summary\":\"最新优化建议\"}"))
                .andExpect(jsonPath("$.data.content[0].taskType").value("RESUME_OPTIMIZE"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.first").value(true))
                .andExpect(jsonPath("$.data.last").value(false));
    }

    @Test
    void queryEndpointsShouldReturnBadRequestWhenUserIdIsBlank() throws Exception {
        mockMvc.perform(get("/api/job-agent/tasks")
                        .param("userId", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));

        mockMvc.perform(get("/api/job-agent/favorites")
                        .param("userId", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));

        mockMvc.perform(get("/api/job-agent/generated-tasks")
                        .param("userId", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));

        mockMvc.perform(get("/api/job-agent/resume-versions")
                        .param("userId", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));
    }

    @Test
    void queryEndpointShouldReturnBadRequestWhenUserIdIsMissing() throws Exception {
        mockMvc.perform(get("/api/job-agent/tasks"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("用户 ID 不能为空"));
    }

    @Test
    void detailEndpointShouldReturnBadRequestWhenUuidIsInvalid() throws Exception {
        mockMvc.perform(get("/api/job-agent/tasks/not-a-uuid")
                        .param("userId", "demo-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请求参数类型不合法: taskId"));
    }

    @Test
    void getGeneratedTaskShouldReturnDetailAndRejectOtherUser() throws Exception {
        UUID taskId = UUID.randomUUID();
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                taskId,
                "demo-user",
                JobGeneratedTaskType.INTERVIEW_PREP,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"target_position\":\"Java 后端开发工程师\"}",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/generated-tasks/{taskId}", taskId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(taskId.toString()))
                .andExpect(jsonPath("$.data.taskType").value("INTERVIEW_PREP"));

        mockMvc.perform(get("/api/job-agent/generated-tasks/{taskId}", taskId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问该求职生成记录: " + taskId));
    }

    @Test
    void deleteGeneratedTaskShouldDeleteOwnedTaskOnly() throws Exception {
        UUID taskId = UUID.randomUUID();
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                taskId,
                "demo-user",
                JobGeneratedTaskType.RESUME_OPTIMIZE,
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                "{\"summary\":\"优化建议\"}",
                Instant.now()
        ));

        mockMvc.perform(delete("/api/job-agent/generated-tasks/{taskId}", taskId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(jobGeneratedTaskRepository.findById(taskId)).isPresent();

        mockMvc.perform(delete("/api/job-agent/generated-tasks/{taskId}", taskId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertThat(jobGeneratedTaskRepository.findById(taskId)).isEmpty();
    }

    @Test
    void listTasksShouldReturnUserJobAnalysisHistory() throws Exception {
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                UUID.randomUUID(),
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG",
                95,
                "{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/tasks")
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data[0].userId").value("demo-user"))
                .andExpect(jsonPath("$.data[0].resumeText").value("Spring Boot FastAPI RAG 项目"))
                .andExpect(jsonPath("$.data[0].jobDescription").value("需要 Java、Spring Boot、FastAPI、RAG"))
                .andExpect(jsonPath("$.data[0].matchScore").value(95))
                .andExpect(jsonPath("$.data[0].resultJson").value("{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}"));
    }

    @Test
    void listTasksPageShouldReturnPagedHistoryWithMetadata() throws Exception {
        Instant now = Instant.now();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                UUID.randomUUID(),
                "demo-user",
                "较早 RAG 项目",
                "需要 Java",
                80,
                "{\"match_score\":80}",
                now.minusSeconds(60)
        ));
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                UUID.randomUUID(),
                "demo-user",
                "最新 RAG 项目",
                "需要 Java、Spring Boot",
                95,
                "{\"match_score\":95}",
                now
        ));
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                UUID.randomUUID(),
                "other-user",
                "其他用户项目",
                "需要 Python",
                88,
                "{\"match_score\":88}",
                now.plusSeconds(60)
        ));

        mockMvc.perform(get("/api/job-agent/tasks/page")
                        .param("userId", "demo-user")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].resumeText").value("最新 RAG 项目"))
                .andExpect(jsonPath("$.data.content[0].matchScore").value(95))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void listTasksPageShouldReturnBadRequestWhenPageOrSizeIsInvalid() throws Exception {
        mockMvc.perform(get("/api/job-agent/tasks/page")
                        .param("userId", "demo-user")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("page 不能小于 0"));

        mockMvc.perform(get("/api/job-agent/tasks/page")
                        .param("userId", "demo-user")
                        .param("page", "0")
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("size 必须在 1 到 50 之间"));
    }

    @Test
    void compareTasksShouldReturnScoreAndSkillComparison() throws Exception {
        UUID taskId1 = UUID.randomUUID();
        UUID taskId2 = UUID.randomUUID();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                taskId1,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                92,
                "{\"match_score\":92,\"matched_skills\":[\"Java\",\"Spring Boot\",\"RAG\"],\"missing_skills\":[\"Redis\"],\"strengths\":[\"RAG 项目完整\"],\"risks\":[\"生产经验不足\"]}",
                Instant.now()
        ));
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                taskId2,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、MySQL、Redis",
                86,
                "{\"match_score\":86,\"matched_skills\":[\"Java\",\"Spring Boot\",\"MySQL\"],\"missing_skills\":[\"Redis\"],\"strengths\":[\"后端经验匹配\"],\"risks\":[\"缓存经验不足\"]}",
                Instant.now()
        ));

        mockMvc.perform(post("/api/job-agent/tasks/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "taskIds": ["%s", "%s"]
                                }
                                """.formatted(taskId1, taskId2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.bestTaskId").value(taskId1.toString()))
                .andExpect(jsonPath("$.data.bestScore").value(92))
                .andExpect(jsonPath("$.data.averageScore").value(89.0))
                .andExpect(jsonPath("$.data.commonMatchedSkills[0]").value("Java"))
                .andExpect(jsonPath("$.data.commonMatchedSkills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.data.commonMissingSkills[0]").value("Redis"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].taskId").value(taskId1.toString()))
                .andExpect(jsonPath("$.data.items[0].matchedSkills[2]").value("RAG"))
                .andExpect(jsonPath("$.data.items[1].risks[0]").value("缓存经验不足"));
    }

    @Test
    void compareTasksShouldRejectOtherUserTask() throws Exception {
        UUID ownTaskId = UUID.randomUUID();
        UUID otherTaskId = UUID.randomUUID();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                ownTaskId,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、RAG",
                92,
                "{\"match_score\":92,\"matched_skills\":[\"Java\"]}",
                Instant.now()
        ));
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                otherTaskId,
                "other-user",
                "Python 项目",
                "需要 Python",
                80,
                "{\"match_score\":80,\"matched_skills\":[\"Python\"]}",
                Instant.now()
        ));

        mockMvc.perform(post("/api/job-agent/tasks/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "taskIds": ["%s", "%s"]
                                }
                                """.formatted(ownTaskId, otherTaskId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问该求职分析记录: " + otherTaskId));
    }

    @Test
    void compareTasksShouldReturnBadRequestWhenTaskCountIsLessThanTwo() throws Exception {
        UUID taskId = UUID.randomUUID();

        mockMvc.perform(post("/api/job-agent/tasks/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "demo-user",
                                  "taskIds": ["%s"]
                                }
                                """.formatted(taskId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("taskIds: 一次需要对比 2 到 5 条求职分析记录"));
    }

    @Test
    void getTaskShouldReturnJobAnalysisDetail() throws Exception {
        UUID taskId = UUID.randomUUID();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                taskId,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG",
                95,
                "{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/tasks/{taskId}", taskId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(taskId.toString()))
                .andExpect(jsonPath("$.data.userId").value("demo-user"))
                .andExpect(jsonPath("$.data.resumeText").value("Spring Boot FastAPI RAG 项目"))
                .andExpect(jsonPath("$.data.jobDescription").value("需要 Java、Spring Boot、FastAPI、RAG"))
                .andExpect(jsonPath("$.data.matchScore").value(95))
                .andExpect(jsonPath("$.data.resultJson").value("{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}"));
    }

    @Test
    void getTaskShouldReturnForbiddenWhenUserDoesNotOwnTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                taskId,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG",
                95,
                "{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}",
                Instant.now()
        ));

        mockMvc.perform(get("/api/job-agent/tasks/{taskId}", taskId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问该求职分析记录: " + taskId));
    }

    @Test
    void deleteTaskShouldRemoveJobAnalysisTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                taskId,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG",
                95,
                "{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}",
                Instant.now()
        ));

        mockMvc.perform(delete("/api/job-agent/tasks/{taskId}", taskId)
                        .param("userId", "demo-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(jobAnalysisTaskRepository.findById(taskId)).isEmpty();
    }

    @Test
    void deleteTaskShouldReturnForbiddenWhenUserDoesNotOwnTask() throws Exception {
        UUID taskId = UUID.randomUUID();
        jobAnalysisTaskRepository.save(new JobAnalysisTask(
                taskId,
                "demo-user",
                "Spring Boot FastAPI RAG 项目",
                "需要 Java、Spring Boot、FastAPI、RAG",
                95,
                "{\"match_score\":95,\"matched_skills\":[\"Spring Boot\"]}",
                Instant.now()
        ));

        mockMvc.perform(delete("/api/job-agent/tasks/{taskId}", taskId)
                        .param("userId", "other-user"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("无权访问该求职分析记录: " + taskId));

        assertThat(jobAnalysisTaskRepository.findById(taskId)).isPresent();
    }
}
