package com.example.aikb.controller;

import com.example.aikb.common.ApiResponse;
import com.example.aikb.common.PageResponse;
import com.example.aikb.security.AuthenticatedUser;
import com.example.aikb.dto.fastapi.FastApiInterviewPrepResponse;
import com.example.aikb.dto.fastapi.FastApiJdParseResponse;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.fastapi.FastApiJobDeliveryPackageResponse;
import com.example.aikb.dto.fastapi.FastApiResumeOptimizeResponse;
import com.example.aikb.dto.fastapi.FastApiResumeParseResponse;
import com.example.aikb.dto.fastapi.FastApiStarInterviewAnswerResponse;
import com.example.aikb.dto.job.InterviewPrepRequest;
import com.example.aikb.dto.job.JdParseRequest;
import com.example.aikb.dto.job.JobAnalysisTaskResponse;
import com.example.aikb.dto.job.JobAnalyzeRequest;
import com.example.aikb.dto.job.JobDeliveryPackageRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

import static com.example.aikb.common.PageRequests.of;
import static com.example.aikb.common.CurrentUserIdentity.userIdOrRequestParam;

@RestController
@RequestMapping("/api/job-agent")
public class JobAgentController {

    private final JobAgentService jobAgentService;

    public JobAgentController(JobAgentService jobAgentService) {
        this.jobAgentService = jobAgentService;
    }

    @PostMapping("/analyze")
    public ApiResponse<FastApiJobAnalyzeResponse> analyze(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody JobAnalyzeRequest request
    ) {
        return ApiResponse.ok(jobAgentService.analyze(new JobAnalyzeRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.resumeText(),
                request.jobDescription()
        )));
    }

    @PostMapping(value = "/analyze-from-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FastApiJobAnalyzeResponse> analyzeFromFile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam String resumeText,
            @RequestParam MultipartFile file
    ) {
        return ApiResponse.ok(jobAgentService.analyzeFromFile(
                userIdOrRequestParam(currentUser, userId),
                resumeText,
                file
        ));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<JobAnalysisTaskResponse>> listTasks(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.listTasks(userIdOrRequestParam(currentUser, userId)));
    }

    @GetMapping("/tasks/page")
    public ApiResponse<PageResponse<JobAnalysisTaskResponse>> listTasksPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listTasksPage(userIdOrRequestParam(currentUser, userId), of(page, size)));
    }

    @PostMapping("/tasks/compare")
    public ApiResponse<JobTaskCompareResponse> compareTasks(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody JobTaskCompareRequest request
    ) {
        return ApiResponse.ok(jobAgentService.compareTasks(new JobTaskCompareRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.taskIds()
        )));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<JobAnalysisTaskResponse> getTask(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID taskId,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.getTask(taskId, userIdOrRequestParam(currentUser, userId)));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ApiResponse<Void> deleteTask(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID taskId,
            @RequestParam(required = false) String userId
    ) {
        jobAgentService.deleteTask(taskId, userIdOrRequestParam(currentUser, userId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/favorites")
    public ApiResponse<JobFavoriteResponse> createFavorite(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody JobFavoriteRequest request
    ) {
        return ApiResponse.ok(jobAgentService.createFavorite(new JobFavoriteRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.jobTitle(),
                request.companyName(),
                request.jobDescription(),
                request.sourceUrl(),
                request.notes()
        )));
    }

    @GetMapping("/favorites")
    public ApiResponse<List<JobFavoriteResponse>> listFavorites(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.listFavorites(userIdOrRequestParam(currentUser, userId)));
    }

    @GetMapping("/favorites/page")
    public ApiResponse<PageResponse<JobFavoriteResponse>> listFavoritesPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listFavoritesPage(userIdOrRequestParam(currentUser, userId), of(page, size)));
    }

    @GetMapping("/favorites/{favoriteId}")
    public ApiResponse<JobFavoriteResponse> getFavorite(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID favoriteId,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.getFavorite(favoriteId, userIdOrRequestParam(currentUser, userId)));
    }

    @DeleteMapping("/favorites/{favoriteId}")
    public ApiResponse<Void> deleteFavorite(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID favoriteId,
            @RequestParam(required = false) String userId
    ) {
        jobAgentService.deleteFavorite(favoriteId, userIdOrRequestParam(currentUser, userId));
        return ApiResponse.ok(null);
    }

    @GetMapping("/generated-tasks")
    public ApiResponse<List<JobGeneratedTaskResponse>> listGeneratedTasks(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) JobGeneratedTaskType taskType
    ) {
        return ApiResponse.ok(jobAgentService.listGeneratedTasks(userIdOrRequestParam(currentUser, userId), taskType));
    }

    @GetMapping("/generated-tasks/page")
    public ApiResponse<PageResponse<JobGeneratedTaskResponse>> listGeneratedTasksPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) JobGeneratedTaskType taskType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listGeneratedTasksPage(userIdOrRequestParam(currentUser, userId), taskType, of(page, size)));
    }

    @GetMapping("/generated-tasks/{taskId}")
    public ApiResponse<JobGeneratedTaskResponse> getGeneratedTask(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID taskId,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.getGeneratedTask(taskId, userIdOrRequestParam(currentUser, userId)));
    }

    @DeleteMapping("/generated-tasks/{taskId}")
    public ApiResponse<Void> deleteGeneratedTask(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID taskId,
            @RequestParam(required = false) String userId
    ) {
        jobAgentService.deleteGeneratedTask(taskId, userIdOrRequestParam(currentUser, userId));
        return ApiResponse.ok(null);
    }

    @PostMapping("/resume-versions")
    public ApiResponse<JobResumeVersionResponse> createResumeVersion(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody JobResumeVersionRequest request
    ) {
        return ApiResponse.ok(jobAgentService.createResumeVersion(new JobResumeVersionRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.versionName(),
                request.targetRole(),
                request.resumeText(),
                request.notes()
        )));
    }

    @GetMapping("/resume-versions")
    public ApiResponse<List<JobResumeVersionResponse>> listResumeVersions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.listResumeVersions(userIdOrRequestParam(currentUser, userId)));
    }

    @GetMapping("/resume-versions/page")
    public ApiResponse<PageResponse<JobResumeVersionResponse>> listResumeVersionsPage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(jobAgentService.listResumeVersionsPage(userIdOrRequestParam(currentUser, userId), of(page, size)));
    }

    @GetMapping("/resume-versions/{versionId}")
    public ApiResponse<JobResumeVersionResponse> getResumeVersion(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID versionId,
            @RequestParam(required = false) String userId
    ) {
        return ApiResponse.ok(jobAgentService.getResumeVersion(versionId, userIdOrRequestParam(currentUser, userId)));
    }

    @PutMapping("/resume-versions/{versionId}")
    public ApiResponse<JobResumeVersionResponse> updateResumeVersion(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID versionId,
            @Valid @RequestBody JobResumeVersionRequest request
    ) {
        return ApiResponse.ok(jobAgentService.updateResumeVersion(versionId, new JobResumeVersionRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.versionName(),
                request.targetRole(),
                request.resumeText(),
                request.notes()
        )));
    }

    @DeleteMapping("/resume-versions/{versionId}")
    public ApiResponse<Void> deleteResumeVersion(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID versionId,
            @RequestParam(required = false) String userId
    ) {
        jobAgentService.deleteResumeVersion(versionId, userIdOrRequestParam(currentUser, userId));
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
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ResumeOptimizeRequest request
    ) {
        return ApiResponse.ok(jobAgentService.optimizeResume(new ResumeOptimizeRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.resumeText(),
                request.jobDescription()
        )));
    }

    @PostMapping("/interview/prepare")
    public ApiResponse<FastApiInterviewPrepResponse> prepareInterview(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody InterviewPrepRequest request
    ) {
        return ApiResponse.ok(jobAgentService.prepareInterview(new InterviewPrepRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.resumeText(),
                request.jobDescription()
        )));
    }

    @PostMapping("/interview/star-answer")
    public ApiResponse<FastApiStarInterviewAnswerResponse> generateStarInterviewAnswer(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody StarInterviewAnswerRequest request
    ) {
        return ApiResponse.ok(jobAgentService.generateStarInterviewAnswer(new StarInterviewAnswerRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.resumeText(),
                request.jobDescription(),
                request.question()
        )));
    }

    @PostMapping("/delivery-package")
    public ApiResponse<FastApiJobDeliveryPackageResponse> generateJobDeliveryPackage(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody JobDeliveryPackageRequest request
    ) {
        return ApiResponse.ok(jobAgentService.generateJobDeliveryPackage(new JobDeliveryPackageRequest(
                userIdOrRequestParam(currentUser, request.userId()),
                request.resumeText(),
                request.jobDescription()
        )));
    }
}
