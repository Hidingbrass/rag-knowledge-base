package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.job.JobAnalysisTaskResponse;
import com.example.aikb.dto.job.JobAnalyzeRequest;
import com.example.aikb.service.JobAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/job-agent")
public class JobAgentController {

    private final JobAgentService jobAgentService;

    public JobAgentController(JobAgentService jobAgentService) {
        this.jobAgentService = jobAgentService;
    }

    @PostMapping("/analyze")
    public ApiResponse<FastApiJobAnalyzeResponse> analyze(
            @Valid @RequestBody JobAnalyzeRequest request
    ) {
        return ApiResponse.ok(jobAgentService.analyze(request));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<JobAnalysisTaskResponse>> listTasks(
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.listTasks(userId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<JobAnalysisTaskResponse> getTask(
            @PathVariable UUID taskId,
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.getTask(taskId, userId));
    }
}
