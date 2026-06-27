package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.dto.rag.RagAskRequest;
import com.example.aikb.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧版 RAG 问答业务入口。
 *
 * 这个接口曾经用于最小联调：Spring Boot 直接转发到 FastAPI RAG。
 * 现在项目已经接入知识库权限、会话权限和 documentId 归属校验，
 * 所以不能再保留这个直连接口，否则用户可能绕过 /api/chat/sessions/{sessionId}/ask。
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    /**
     * 旧版直连入口已停用。
     *
     * 保留这个方法的目的不是继续提供能力，
     * 而是让误调用旧接口的人得到明确错误提示；
     * 同时测试会保证它不会再调用 FastAPI。
     */
    @Deprecated
    @PostMapping("/ask")
    public ApiResponse<FastApiRagResponse> ask(@Valid @RequestBody RagAskRequest request) {
        throw new BusinessException("旧版 /api/rag/ask 已停用，请使用 /api/chat/sessions/{sessionId}/ask");
    }
}
