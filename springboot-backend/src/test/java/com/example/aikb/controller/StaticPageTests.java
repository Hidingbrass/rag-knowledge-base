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
 * - /index.html 能正常返回；
 * - 页面包含关键演示区域；
 * - 浏览器自动请求 /favicon.ico 时不会被误处理成 500。
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaticPageTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void indexPageShouldBeServedWithDemoSections() throws Exception {
        mockMvc.perform(get("/index.html"))
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
