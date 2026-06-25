package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.knowledgebase.CreateKnowledgeBaseRequest;
import com.example.aikb.dto.knowledgebase.KnowledgeBaseResponse;
import com.example.aikb.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 知识库管理接口。
 *
 * Controller 只做 HTTP 层的事情：
 * - 接收 JSON 请求；
 * - 调用 Service；
 * - 把业务对象转换成响应 DTO。
 */
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseResponse> create(
            @Valid @RequestBody CreateKnowledgeBaseRequest request
    ) {
        return ApiResponse.ok(KnowledgeBaseResponse.from(knowledgeBaseService.create(request)));
    }

    @GetMapping
    public ApiResponse<List<KnowledgeBaseResponse>> list() {
        List<KnowledgeBaseResponse> responses = knowledgeBaseService.list()
                .stream()
                .map(KnowledgeBaseResponse::from)
                .toList();

        return ApiResponse.ok(responses);
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseResponse> get(@PathVariable UUID knowledgeBaseId) {
        return ApiResponse.ok(KnowledgeBaseResponse.from(
                knowledgeBaseService.getRequired(knowledgeBaseId)
        ));
    }
}
