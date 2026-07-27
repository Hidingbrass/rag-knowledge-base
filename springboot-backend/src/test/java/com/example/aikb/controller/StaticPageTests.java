package com.example.aikb.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 对 Vite + Vue3 演示入口、生产资源和 History 路由做轻量回归保护。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticPageTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indexPageShouldLoadBuiltZhiTuApplication() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta charset=\"UTF-8\">")))
                .andExpect(content().string(containsString("content=\"ZhiTu AI\"")))
                .andExpect(content().string(containsString("data-product=\"zhitu-ai\"")))
                .andExpect(content().string(containsString("/assets/zhitu-app.js")))
                .andExpect(content().string(containsString("/assets/zhitu-style.css")));
    }

    @Test
    void builtApplicationShouldKeepAuthenticationRagAndCareerCapabilities() throws Exception {
        mockMvc.perform(get("/assets/zhitu-app.js"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/javascript"))
                .andExpect(content().string(containsString("/api/auth/login")))
                .andExpect(content().string(containsString("/api/auth/me")))
                .andExpect(content().string(containsString("/api/knowledge-bases")))
                .andExpect(content().string(containsString("/api/chat/sessions")))
                .andExpect(content().string(containsString("/api/job-agent/resume/parse")))
                .andExpect(content().string(containsString("/api/job-agent/resume/optimize")))
                .andExpect(content().string(containsString("/api/job-agent/interview/prepare")))
                .andExpect(content().string(containsString("/api/job-agent/delivery-package")));
    }

    @Test
    void applicationRoutesShouldForwardToVueEntry() throws Exception {
        for (String route : new String[]{"/library", "/study", "/career"}) {
            mockMvc.perform(get(route))
                    .andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"));
        }
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
