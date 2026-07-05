package com.example.aikb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 静态演示页面测试。
 *
 * 这个页面不是核心业务逻辑，但它是面试演示入口。
 * 所以这里做轻量保护：
 * - /index.html 能正常返回企业工作台；
 * - /debug.html 保留原始联调页；
 * - 浏览器自动请求 /favicon.ico 时不会被误处理成 500。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticPageTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indexPageShouldBeServedWithVueWorkspace() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta charset=\"UTF-8\">")))
                .andExpect(content().string(containsString("https://unpkg.com/vue@3")))
                .andExpect(content().string(containsString("createApp")))
                .andExpect(content().string(containsString("__aikbApp")))
                .andExpect(content().string(containsString("debug.html")))
                .andExpect(content().string(containsString("activeTab")))
                .andExpect(content().string(containsString("loadInitialData")))
                .andExpect(content().string(containsString("aiCallLogs")))
                .andExpect(content().string(containsString("loadAiCallLogs")))
                .andExpect(content().string(containsString("normalizedJobResult")))
                .andExpect(content().string(containsString("normalizedResumeParseResult")))
                .andExpect(content().string(containsString("normalizedJdParseResult")))
                .andExpect(content().string(containsString("normalizedResumeOptimizeResult")))
                .andExpect(content().string(containsString("normalizedInterviewPrepResult")))
                .andExpect(content().string(containsString("normalizedStarInterviewAnswerResult")))
                .andExpect(content().string(containsString("selectedCompareTaskIds")))
                .andExpect(content().string(containsString("generatedTaskFilter")))
                .andExpect(content().string(containsString("selectedGeneratedTask")))
                .andExpect(content().string(containsString("selectedGeneratedTaskResultText")))
                .andExpect(content().string(containsString("jobPages")))
                .andExpect(content().string(containsString("pageTotal")))
                .andExpect(content().string(containsString("pagination")))
                .andExpect(content().string(containsString("jobPages.tasks.page - 1")))
                .andExpect(content().string(containsString("jobPages.tasks.page + 1")))
                .andExpect(content().string(containsString("jobFavorites")))
                .andExpect(content().string(containsString("jobGeneratedTasks")))
                .andExpect(content().string(containsString("jobResumeVersions")))
                .andExpect(content().string(containsString("job-result-grid")))
                .andExpect(content().string(containsString("resume-summary-grid")))
                .andExpect(content().string(containsString("compare-dashboard")))
                .andExpect(content().string(containsString("compare-best-score")))
                .andExpect(content().string(containsString("score-bar-fill")))
                .andExpect(content().string(containsString("normalizeJobResult")))
                .andExpect(content().string(containsString("normalizeResumeParseResult")))
                .andExpect(content().string(containsString("normalizeJdParseResult")))
                .andExpect(content().string(containsString("normalizeResumeOptimizeResult")))
                .andExpect(content().string(containsString("normalizeInterviewPrepResult")))
                .andExpect(content().string(containsString("normalizeStarInterviewAnswerResult")))
                .andExpect(content().string(containsString("/api/job-agent/resume/parse")))
                .andExpect(content().string(containsString("/api/job-agent/jd/parse")))
                .andExpect(content().string(containsString("/api/job-agent/resume/optimize")))
                .andExpect(content().string(containsString("/api/job-agent/interview/prepare")))
                .andExpect(content().string(containsString("/api/job-agent/interview/star-answer")))
                .andExpect(content().string(containsString("/api/ai-call-logs/recent")))
                .andExpect(content().string(containsString("/api/job-agent/tasks/page")))
                .andExpect(content().string(containsString("/api/job-agent/favorites")))
                .andExpect(content().string(containsString("/api/job-agent/favorites/page")))
                .andExpect(content().string(containsString("/api/job-agent/generated-tasks")))
                .andExpect(content().string(containsString("/api/job-agent/generated-tasks/page")))
                .andExpect(content().string(containsString("/api/job-agent/resume-versions")))
                .andExpect(content().string(containsString("/api/job-agent/resume-versions/page")))
                .andExpect(content().string(containsString("/api/job-agent/tasks/compare")))
                .andExpect(content().string(containsString("parseJd")))
                .andExpect(content().string(containsString("optimizeResume")))
                .andExpect(content().string(containsString("prepareInterview")))
                .andExpect(content().string(containsString("generateStarInterviewAnswer")))
                .andExpect(content().string(containsString("createJobFavorite")))
                .andExpect(content().string(containsString("loadJobGeneratedTasks")))
                .andExpect(content().string(containsString("generatedTaskPageQuery")))
                .andExpect(content().string(containsString("onGeneratedTaskFilterChange")))
                .andExpect(content().string(containsString("taskType=")))
                .andExpect(content().string(containsString("parseJsonObject")))
                .andExpect(content().string(containsString("viewJobGeneratedTask")))
                .andExpect(content().string(containsString("deleteJobGeneratedTask")))
                .andExpect(content().string(containsString("createResumeVersion")))
                .andExpect(content().string(containsString("loadResumeVersions")))
                .andExpect(content().string(containsString("useResumeVersion")))
                .andExpect(content().string(containsString("updateResumeVersion")))
                .andExpect(content().string(containsString("deleteResumeVersion")))
                .andExpect(content().string(containsString("compareSelectedJobTasks")))
                .andExpect(content().string(containsString("isBestCompareItem")))
                .andExpect(content().string(containsString("scorePercent")))
                .andExpect(content().string(containsString("exportJobAnalysisReport")))
                .andExpect(content().string(containsString("exportResumeOptimizeReport")))
                .andExpect(content().string(containsString("exportInterviewPrepReport")))
                .andExpect(content().string(containsString("exportStarInterviewAnswerReport")))
                .andExpect(content().string(containsString("downloadMarkdown")))
                .andExpect(content().string(containsString("new Blob")))
                .andExpect(content().string(containsString("job-analysis-report.md")))
                .andExpect(content().string(containsString("resume-optimize-report.md")))
                .andExpect(content().string(containsString("interview-prep-report.md")))
                .andExpect(content().string(containsString("star-interview-answer.md")))
                .andExpect(content().string(containsString("viewJobTask")))
                .andExpect(content().string(containsString("deleteJobTask")))
                .andExpect(content().string(containsString("method: \"DELETE\"")));
    }

    @Test
    void debugPageShouldKeepOriginalIntegrationConsole() throws Exception {
        mockMvc.perform(get("/debug.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta charset=\"UTF-8\">")))
                .andExpect(content().string(containsString("demo-steps")))
                .andExpect(content().string(containsString("checkHealth()")))
                .andExpect(content().string(containsString("quick-buttons")))
                .andExpect(content().string(containsString("availableDocCount")))
                .andExpect(content().string(containsString("nextActionBox")))
                .andExpect(content().string(containsString("jobTaskCount")))
                .andExpect(content().string(containsString("analyzeJob()")))
                .andExpect(content().string(containsString("viewJobTaskDetail")))
                .andExpect(content().string(containsString("deleteJobTask")))
                .andExpect(content().string(containsString("method: \"DELETE\"")));
    }

    @Test
    void faviconShouldReturnNotFoundInsteadOfInternalServerError() throws Exception {
        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("资源不存在：favicon.ico"));
    }
}
