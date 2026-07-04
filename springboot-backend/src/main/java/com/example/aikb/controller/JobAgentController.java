package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.common.PageResponse;
import com.example.aikb.dto.fastapi.FastApiInterviewPrepResponse;
import com.example.aikb.dto.fastapi.FastApiJdParseResponse;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.fastapi.FastApiResumeOptimizeResponse;
import com.example.aikb.dto.fastapi.FastApiResumeParseResponse;
import com.example.aikb.dto.fastapi.FastApiStarInterviewAnswerResponse;
import com.example.aikb.dto.job.InterviewPrepRequest;
import com.example.aikb.dto.job.JdParseRequest;
import com.example.aikb.dto.job.JobAnalysisTaskResponse;
import com.example.aikb.dto.job.JobAnalyzeRequest;
import com.example.aikb.dto.job.JobFavoriteRequest;
import com.example.aikb.dto.job.JobFavoriteResponse;
import com.example.aikb.dto.job.JobGeneratedTaskResponse;
import com.example.aikb.dto.job.JobResumeVersionRequest;
import com.example.aikb.dto.job.JobResumeVersionResponse;
import com.example.aikb.dto.job.JobTaskCompareRequest;
import com.example.aikb.dto.job.JobTaskCompareResponse;
import com.example.aikb.dto.job.ResumeOptimizeRequest;
import com.example.aikb.dto.job.ResumeParseRequest;
import com.example.aikb.dto.job.StarInterviewAnswerRequest;
import com.example.aikb.entity.JobGeneratedTaskType;
import com.example.aikb.service.JobAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.example.aikb.common.PageRequests.of;

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

    @GetMapping("/tasks/page")
    public ApiResponse<PageResponse<JobAnalysisTaskResponse>> listTasksPage(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listTasksPage(userId, of(page, size)));
    }

    @PostMapping("/tasks/compare")
    public ApiResponse<JobTaskCompareResponse> compareTasks(
            @Valid @RequestBody JobTaskCompareRequest request
    ) {
        return ApiResponse.ok(jobAgentService.compareTasks(request));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<JobAnalysisTaskResponse> getTask(
            @PathVariable UUID taskId,
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.getTask(taskId, userId));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(
            @PathVariable UUID taskId,
            @RequestParam String userId
    ) {
        jobAgentService.deleteTask(taskId, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/favorites")
    public ApiResponse<JobFavoriteResponse> createFavorite(
            @Valid @RequestBody JobFavoriteRequest request
    ) {
        return ApiResponse.ok(jobAgentService.createFavorite(request));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<JobFavoriteResponse>> listFavorites(
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.listFavorites(userId));
    }

    @GetMapping("/favorites/page")
    public ApiResponse<PageResponse<JobFavoriteResponse>> listFavoritesPage(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listFavoritesPage(userId, of(page, size)));
    }

    @GetMapping("/favorites/{favoriteId}")
    public ApiResponse<JobFavoriteResponse> getFavorite(
            @PathVariable UUID favoriteId,
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.getFavorite(favoriteId, userId));
    }

    @DeleteMapping("/favorites/{favoriteId}")
    public ApiResponse<Void> deleteFavorite(
            @PathVariable UUID favoriteId,
            @RequestParam String userId
    ) {
        jobAgentService.deleteFavorite(favoriteId, userId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/generated-tasks")
    public ApiResponse<List<JobGeneratedTaskResponse>> listGeneratedTasks(
            @RequestParam String userId,
            @RequestParam(required = false) JobGeneratedTaskType taskType
    ) {
        return ApiResponse.ok(jobAgentService.listGeneratedTasks(userId, taskType));
    }

    @GetMapping("/generated-tasks/page")
    public ApiResponse<PageResponse<JobGeneratedTaskResponse>> listGeneratedTasksPage(
            @RequestParam String userId,
            @RequestParam(required = false) JobGeneratedTaskType taskType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listGeneratedTasksPage(userId, taskType, of(page, size)));
    }

    @GetMapping("/generated-tasks/{taskId}")
    public ApiResponse<JobGeneratedTaskResponse> getGeneratedTask(
            @PathVariable UUID taskId,
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.getGeneratedTask(taskId, userId));
    }

    @DeleteMapping("/generated-tasks/{taskId}")
    public ApiResponse<Void> deleteGeneratedTask(
            @PathVariable UUID taskId,
            @RequestParam String userId
    ) {
        jobAgentService.deleteGeneratedTask(taskId, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/resume-versions")
    public ApiResponse<JobResumeVersionResponse> createResumeVersion(
            @Valid @RequestBody JobResumeVersionRequest request
    ) {
        return ApiResponse.ok(jobAgentService.createResumeVersion(request));
    }

    @GetMapping("/resume-versions")
    public ApiResponse<List<JobResumeVersionResponse>> listResumeVersions(
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.listResumeVersions(userId));
    }

    @GetMapping("/resume-versions/page")
    public ApiResponse<PageResponse<JobResumeVersionResponse>> listResumeVersionsPage(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listResumeVersionsPage(userId, of(page, size)));
    }

    @GetMapping("/resume-versions/{versionId}")
    public ApiResponse<JobResumeVersionResponse> getResumeVersion(
            @PathVariable UUID versionId,
            @RequestParam String userId
    ) {
        return ApiResponse.ok(jobAgentService.getResumeVersion(versionId, userId));
    }

    @PutMapping("/resume-versions/{versionId}")
    public ApiResponse<JobResumeVersionResponse> updateResumeVersion(
            @PathVariable UUID versionId,
            @Valid @RequestBody JobResumeVersionRequest request
    ) {
        return ApiResponse.ok(jobAgentService.updateResumeVersion(versionId, request));
    }

    @DeleteMapping("/resume-versions/{versionId}")
    public ApiResponse<Void> deleteResumeVersion(
            @PathVariable UUID versionId,
            @RequestParam String userId
    ) {
        jobAgentService.deleteResumeVersion(versionId, userId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/resume/parse")
    public ApiResponse<FastApiResumeParseResponse> parseResume(
            @Valid @RequestBody ResumeParseRequest request
    ) {
        return ApiResponse.ok(jobAgentService.parseResume(request));
    }

    @PostMapping("/jd/parse")
    public ApiResponse<FastApiJdParseResponse> parseJd(
            @Valid @RequestBody JdParseRequest request
    ) {
        return ApiResponse.ok(jobAgentService.parseJd(request));
    }

    @PostMapping("/resume/optimize")
    public ApiResponse<FastApiResumeOptimizeResponse> optimizeResume(
            @Valid @RequestBody ResumeOptimizeRequest request
    ) {
        return ApiResponse.ok(jobAgentService.optimizeResume(request));
    }

    @PostMapping("/interview/prepare")
    public ApiResponse<FastApiInterviewPrepResponse> prepareInterview(
            @Valid @RequestBody InterviewPrepRequest request
    ) {
        return ApiResponse.ok(jobAgentService.prepareInterview(request));
    }

    @PostMapping("/interview/star-answer")
    public ApiResponse<FastApiStarInterviewAnswerResponse> generateStarInterviewAnswer(
            @Valid @RequestBody StarInterviewAnswerRequest request
    ) {
        return ApiResponse.ok(jobAgentService.generateStarInterviewAnswer(request));
    }
}
