package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.aicall.AiCallLogResponse;
import com.example.aikb.dto.aicall.AiCallSummaryResponse;
import com.example.aikb.entity.AiCallLog;
import com.example.aikb.service.AiCallLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-call-logs")
public class AiCallLogController {

    private final AiCallLogService service;

    public AiCallLogController(AiCallLogService service) {
        this.service = service;
    }

    @GetMapping("/recent")
    public ApiResponse<List<AiCallLogResponse>> listRecentLogs() {
        return ApiResponse.ok(service.listRecentLogs()
                .stream()
                .map(this::toResponse)
                .toList());
    }

    @GetMapping("/summary")
    public ApiResponse<AiCallSummaryResponse> summary(
            @RequestParam(defaultValue = "24") int hours
    ) {
        return ApiResponse.ok(service.summarize(hours));
    }

    private AiCallLogResponse toResponse(AiCallLog log) {
        return new AiCallLogResponse(
                log.id(),
                log.provider(),
                log.businessType(),
                log.endpoint(),
                log.success(),
                log.elapsedMs(),
                log.errorMessage(),
                log.modelNames(),
                log.upstreamCallCount(),
                log.retryCount(),
                log.promptTokens(),
                log.completionTokens(),
                log.totalTokens(),
                log.estimatedCostYuan(),
                log.createdAt()
        );
    }
}
