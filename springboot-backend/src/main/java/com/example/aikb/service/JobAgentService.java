package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.common.PageResponse;
import com.example.aikb.dto.fastapi.FastApiInterviewPrepResponse;
import com.example.aikb.dto.fastapi.FastApiJdParseResponse;
import com.example.aikb.dto.fastapi.FastApiJobAttachmentTextResponse;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.fastapi.FastApiJobDeliveryPackageResponse;
import com.example.aikb.dto.fastapi.FastApiResumeOptimizeResponse;
import com.example.aikb.dto.fastapi.FastApiResumeParseResponse;
import com.example.aikb.dto.fastapi.FastApiStarInterviewAnswerResponse;
import com.example.aikb.dto.job.InterviewPrepRequest;
import com.example.aikb.dto.job.JdParseRequest;
import com.example.aikb.dto.job.JobAnalysisTaskResponse;
import com.example.aikb.dto.job.JobAnalyzeFromFileResponse;
import com.example.aikb.dto.job.JobAnalyzeRequest;
import com.example.aikb.dto.job.JobDeliveryPackageRequest;
import com.example.aikb.dto.job.JobFavoriteRequest;
import com.example.aikb.dto.job.JobFavoriteResponse;
import com.example.aikb.dto.job.JobGeneratedTaskResponse;
import com.example.aikb.dto.job.JobGeneratedTaskReviewRequest;
import com.example.aikb.dto.job.JobResumeVersionRequest;
import com.example.aikb.dto.job.JobResumeVersionResponse;
import com.example.aikb.dto.job.JobTaskCompareItem;
import com.example.aikb.dto.job.JobTaskCompareRequest;
import com.example.aikb.dto.job.JobTaskCompareResponse;
import com.example.aikb.dto.job.ResumeOptimizeRequest;
import com.example.aikb.dto.job.ResumeParseRequest;
import com.example.aikb.dto.job.StarInterviewAnswerRequest;
import com.example.aikb.entity.JobAnalysisTask;
import com.example.aikb.entity.JobFavorite;
import com.example.aikb.entity.JobGeneratedTask;
import com.example.aikb.entity.JobGeneratedTaskType;
import com.example.aikb.entity.JobReviewStatus;
import com.example.aikb.entity.JobResumeVersion;
import com.example.aikb.entity.JobTaskStatus;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.JobAnalysisTaskRepository;
import com.example.aikb.repository.JobFavoriteRepository;
import com.example.aikb.repository.JobGeneratedTaskRepository;
import com.example.aikb.repository.JobResumeVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.example.aikb.common.RequestIdentity.requireUserId;

/**
 * 求职辅助 Agent 业务服务。
 * <p>
 * Service 层负责业务编排：
 * - 接收 Spring Boot 入口 DTO；
 * - 调用 FastAPI AI 服务；
 * - 保存求职分析任务；
 * - 查询历史记录并校验详情访问权限。
 */
@Service
public class JobAgentService {

    private final FastApiRagClient fastApiRagClient;
    private final JobAnalysisTaskRepository jobAnalysisTaskRepository;
    private final JobFavoriteRepository jobFavoriteRepository;
    private final JobGeneratedTaskRepository jobGeneratedTaskRepository;
    private final JobResumeVersionRepository jobResumeVersionRepository;
    private final AiRateLimitService aiRateLimitService;
    private final ObjectMapper objectMapper;

    public JobAgentService(
            FastApiRagClient fastApiRagClient,
            JobAnalysisTaskRepository jobAnalysisTaskRepository,
            JobFavoriteRepository jobFavoriteRepository,
            JobGeneratedTaskRepository jobGeneratedTaskRepository,
            JobResumeVersionRepository jobResumeVersionRepository,
            AiRateLimitService aiRateLimitService,
            ObjectMapper objectMapper
    ) {
        this.fastApiRagClient = fastApiRagClient;
        this.jobAnalysisTaskRepository = jobAnalysisTaskRepository;
        this.jobFavoriteRepository = jobFavoriteRepository;
        this.jobGeneratedTaskRepository = jobGeneratedTaskRepository;
        this.jobResumeVersionRepository = jobResumeVersionRepository;
        this.aiRateLimitService = aiRateLimitService;
        this.objectMapper = objectMapper;
    }

    public FastApiJobAnalyzeResponse analyze(JobAnalyzeRequest request) {
        String userId = requireUserId(request.userId());
        aiRateLimitService.checkAiCallAllowed(userId, "JOB_ANALYZE");
        FastApiJobAnalyzeResponse response = fastApiRagClient.analyzeJob(
                request.resumeText(),
                request.jobDescription()
        );
        String resultJson = toResultJson(response);
        JobAnalysisTask task = new JobAnalysisTask(
                UUID.randomUUID(),
                userId,
                request.resumeText(),
                request.jobDescription(),
                response.matchScore(),
                resultJson,
                Instant.now()
        );
        jobAnalysisTaskRepository.save(task);
        return response;
    }

    public JobAnalyzeFromFileResponse analyzeFromFile(String userId, String resumeText, MultipartFile file) {
        String requiredUserId = requireUserId(userId);
        if (resumeText == null || resumeText.isBlank()) {
            throw new BusinessException("简历内容不能为空");
        }

        aiRateLimitService.checkAiCallAllowed(requiredUserId, "JOB_ANALYZE_FROM_FILE");
        FastApiJobAttachmentTextResponse extracted = fastApiRagClient.extractJdText(file);
        if (extracted.text() == null || extracted.text().isBlank()) {
            throw new BusinessException("岗位附件未识别出有效文字");
        }

        FastApiJobAnalyzeResponse response = fastApiRagClient.analyzeJob(
                resumeText,
                extracted.text()
        );
        String resultJson = toResultJson(response);
        JobAnalysisTask task = new JobAnalysisTask(
                UUID.randomUUID(),
                requiredUserId,
                resumeText,
                extracted.text(),
                response.matchScore(),
                resultJson,
                Instant.now()
        );
        jobAnalysisTaskRepository.save(task);
        return new JobAnalyzeFromFileResponse(
                response,
                extracted.text(),
                extracted.filename(),
                extracted.sourceType(),
                extracted.warnings()
        );
    }

    private String toResultJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new BusinessException("序列化求职 Agent 结果失败", e);
        }
    }

    private JobAnalysisTaskResponse toResponse(JobAnalysisTask task) {
        return new JobAnalysisTaskResponse(
                task.id(),
                task.userId(),
                task.resumeText(),
                task.jobDescription(),
                task.matchScore(),
                task.resultJson(),
                task.createdAt()
        );
    }

    public List<JobAnalysisTaskResponse> listTasks(String userId) {
        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc(requireUserId(userId));
        return tasks
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public PageResponse<JobAnalysisTaskResponse> listTasksPage(String userId, Pageable pageable) {
        return PageResponse.from(
                jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc(requireUserId(userId), pageable),
                this::toResponse
        );
    }

    public JobAnalysisTaskResponse getTask(UUID taskId, String userId) {
        JobAnalysisTask task = getOwnedTask(taskId, userId);
        return toResponse(task);
    }

    private JobAnalysisTask getOwnedTask(UUID taskId, String userId) {
        String requiredUserId = requireUserId(userId);
        JobAnalysisTask task = jobAnalysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("求职分析记录不存在: " + taskId));
        if (!task.userId().equals(requiredUserId)) {
            throw new ForbiddenException("无权访问该求职分析记录: " + taskId);
        }

        return task;
    }

    public void deleteTask(UUID taskId, String userId) {
        JobAnalysisTask task = getOwnedTask(taskId, userId);
        jobAnalysisTaskRepository.delete(task);
    }

    public JobTaskCompareResponse compareTasks(JobTaskCompareRequest request) {
        String userId = requireUserId(request.userId());
        List<JobTaskCompareItem> items = request.taskIds()
                .stream()
                .map(taskId -> toCompareItem(getOwnedTask(taskId, userId)))
                .toList();
        JobTaskCompareItem bestItem = items
                .stream()
                .max((left, right) -> Integer.compare(left.matchScore(), right.matchScore()))
                .orElseThrow(() -> new BusinessException("对比任务不能为空"));
        double averageScore = items
                .stream()
                .mapToInt(JobTaskCompareItem::matchScore)
                .average()
                .orElse(0);

        return new JobTaskCompareResponse(
                bestItem.taskId(),
                bestItem.matchScore(),
                Math.round(averageScore * 10.0) / 10.0,
                commonValues(items.stream().map(JobTaskCompareItem::matchedSkills).toList()),
                commonValues(items.stream().map(JobTaskCompareItem::missingSkills).toList()),
                items
        );
    }

    private JobTaskCompareItem toCompareItem(JobAnalysisTask task) {
        JsonNode result = readResultJson(task.resultJson());
        return new JobTaskCompareItem(
                task.id(),
                task.matchScore(),
                task.jobDescription(),
                stringList(result, "matched_skills"),
                stringList(result, "missing_skills"),
                stringList(result, "strengths"),
                stringList(result, "risks"),
                task.createdAt()
        );
    }

    private JsonNode readResultJson(String resultJson) {
        try {
            return objectMapper.readTree(resultJson);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> stringList(JsonNode node, String fieldName) {
        JsonNode arrayNode = node.get(fieldName);
        if (arrayNode == null || !arrayNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        arrayNode.forEach(item -> {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        });
        return values;
    }

    private List<String> commonValues(List<List<String>> values) {
        if (values.isEmpty()) {
            return List.of();
        }
        Set<String> common = new LinkedHashSet<>(values.get(0));
        values.stream()
                .skip(1)
                .forEach(items -> common.retainAll(new LinkedHashSet<>(items)));
        return List.copyOf(common);
    }

    public FastApiResumeParseResponse parseResume(ResumeParseRequest request) {
        return fastApiRagClient.parseResume(request.resumeText());
    }

    public FastApiJdParseResponse parseJd(JdParseRequest request) {
        return fastApiRagClient.parseJd(request.jobDescription());
    }

    public FastApiResumeOptimizeResponse optimizeResume(ResumeOptimizeRequest request) {
        String userId = requireUserId(request.userId());
        String jobDescription = request.jobDescription() == null ? "" : request.jobDescription();
        aiRateLimitService.checkAiCallAllowed(userId, "RESUME_OPTIMIZE");
        FastApiResumeOptimizeResponse response = fastApiRagClient.optimizeResume(
                request.resumeText(),
                jobDescription
        );
        saveGeneratedTask(
                userId,
                JobGeneratedTaskType.RESUME_OPTIMIZE,
                request.resumeText(),
                jobDescription,
                response
        );
        return response;
    }

    public FastApiInterviewPrepResponse prepareInterview(InterviewPrepRequest request) {
        String userId = requireUserId(request.userId());
        aiRateLimitService.checkAiCallAllowed(userId, "INTERVIEW_PREP");
        FastApiInterviewPrepResponse response = fastApiRagClient.prepareInterview(
                request.resumeText(),
                request.jobDescription()
        );
        saveGeneratedTask(
                userId,
                JobGeneratedTaskType.INTERVIEW_PREP,
                request.resumeText(),
                request.jobDescription(),
                response
        );
        return response;
    }

    public FastApiStarInterviewAnswerResponse generateStarInterviewAnswer(StarInterviewAnswerRequest request) {
        String userId = requireUserId(request.userId());
        aiRateLimitService.checkAiCallAllowed(userId, "STAR_INTERVIEW_ANSWER");
        FastApiStarInterviewAnswerResponse response = fastApiRagClient.generateStarInterviewAnswer(
                request.resumeText(),
                request.jobDescription(),
                request.question()
        );
        saveGeneratedTask(
                userId,
                JobGeneratedTaskType.STAR_INTERVIEW_ANSWER,
                request.resumeText(),
                request.jobDescription(),
                response
        );
        return response;
    }

    public FastApiJobDeliveryPackageResponse generateJobDeliveryPackage(JobDeliveryPackageRequest request) {
        String userId = requireUserId(request.userId());
        aiRateLimitService.checkAiCallAllowed(userId, "JOB_DELIVERY_PACKAGE");
        FastApiJobDeliveryPackageResponse response = fastApiRagClient.generateJobDeliveryPackage(
                request.resumeText(),
                request.jobDescription()
        );
        saveGeneratedTask(
                userId,
                JobGeneratedTaskType.JOB_DELIVERY_PACKAGE,
                request.resumeText(),
                request.jobDescription(),
                response
        );
        return response;
    }

    private void saveGeneratedTask(String userId, JobGeneratedTaskType taskType, String resumeText, String jobDescription, Object response) {
        jobGeneratedTaskRepository.save(new JobGeneratedTask(
                UUID.randomUUID(),
                userId,
                taskType,
                resumeText,
                jobDescription,
                toResultJson(response),
                Instant.now()
        ));
    }

    public List<JobGeneratedTaskResponse> listGeneratedTasks(String userId, JobGeneratedTaskType taskType) {
        String requiredUserId = requireUserId(userId);
        List<JobGeneratedTask> tasks = taskType == null
                ? jobGeneratedTaskRepository.findByUserIdOrderByCreatedAtDesc(requiredUserId)
                : jobGeneratedTaskRepository.findByUserIdAndTaskTypeOrderByCreatedAtDesc(requiredUserId, taskType);
        return tasks.stream()
                .map(this::toGeneratedTaskResponse)
                .toList();
    }

    public PageResponse<JobGeneratedTaskResponse> listGeneratedTasksPage(
            String userId,
            JobGeneratedTaskType taskType,
            Pageable pageable
    ) {
        String requiredUserId = requireUserId(userId);
        return PageResponse.from(
                taskType == null
                        ? jobGeneratedTaskRepository.findByUserIdOrderByCreatedAtDesc(requiredUserId, pageable)
                        : jobGeneratedTaskRepository.findByUserIdAndTaskTypeOrderByCreatedAtDesc(requiredUserId, taskType, pageable),
                this::toGeneratedTaskResponse
        );
    }

    public JobGeneratedTaskResponse getGeneratedTask(UUID taskId, String userId) {
        return toGeneratedTaskResponse(getOwnedGeneratedTask(taskId, userId));
    }

    public void deleteGeneratedTask(UUID taskId, String userId) {
        JobGeneratedTask task = getOwnedGeneratedTask(taskId, userId);
        jobGeneratedTaskRepository.delete(task);
    }

    public JobGeneratedTaskResponse reviewGeneratedTask(
            UUID taskId,
            JobGeneratedTaskReviewRequest request
    ) {
        JobGeneratedTask task = getOwnedGeneratedTask(taskId, request.userId());
        if (request.status() == JobReviewStatus.PENDING_REVIEW) {
            throw new BusinessException("审核结果只能是通过或驳回");
        }
        task.review(request.status(), request.comment(), Instant.now());
        return toGeneratedTaskResponse(jobGeneratedTaskRepository.save(task));
    }

    private JobGeneratedTask getOwnedGeneratedTask(UUID taskId, String userId) {
        String requiredUserId = requireUserId(userId);
        JobGeneratedTask task = jobGeneratedTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("求职生成记录不存在: " + taskId));
        if (!task.userId().equals(requiredUserId)) {
            throw new ForbiddenException("无权访问该求职生成记录: " + taskId);
        }

        return task;
    }

    private JobGeneratedTaskResponse toGeneratedTaskResponse(JobGeneratedTask task) {
        return new JobGeneratedTaskResponse(
                task.id(),
                task.userId(),
                task.taskType(),
                task.status(),
                task.errorMessage(),
                task.reviewStatus(),
                task.reviewComment(),
                task.reviewedAt(),
                task.resumeText(),
                task.jobDescription(),
                task.resultJson(),
                task.createdAt()
        );
    }

    public JobFavoriteResponse createFavorite(JobFavoriteRequest request) {
        String userId = requireUserId(request.userId());
        JobFavorite favorite = new JobFavorite(
                UUID.randomUUID(),
                userId,
                request.jobTitle(),
                request.companyName(),
                request.jobDescription(),
                request.sourceUrl(),
                request.notes(),
                Instant.now()
        );
        return toFavoriteResponse(jobFavoriteRepository.save(favorite));
    }

    public List<JobFavoriteResponse> listFavorites(String userId) {
        return jobFavoriteRepository.findByUserIdOrderByCreatedAtDesc(requireUserId(userId))
                .stream()
                .map(this::toFavoriteResponse)
                .toList();
    }

    public PageResponse<JobFavoriteResponse> listFavoritesPage(String userId, Pageable pageable) {
        return PageResponse.from(
                jobFavoriteRepository.findByUserIdOrderByCreatedAtDesc(requireUserId(userId), pageable),
                this::toFavoriteResponse
        );
    }

    public JobFavoriteResponse getFavorite(UUID favoriteId, String userId) {
        return toFavoriteResponse(getOwnedFavorite(favoriteId, userId));
    }

    public void deleteFavorite(UUID favoriteId, String userId) {
        JobFavorite favorite = getOwnedFavorite(favoriteId, userId);
        jobFavoriteRepository.delete(favorite);
    }

    private JobFavorite getOwnedFavorite(UUID favoriteId, String userId) {
        String requiredUserId = requireUserId(userId);
        JobFavorite favorite = jobFavoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new BusinessException("收藏岗位不存在: " + favoriteId));
        if (!favorite.userId().equals(requiredUserId)) {
            throw new ForbiddenException("无权访问该收藏岗位: " + favoriteId);
        }

        return favorite;
    }

    private JobFavoriteResponse toFavoriteResponse(JobFavorite favorite) {
        return new JobFavoriteResponse(
                favorite.id(),
                favorite.userId(),
                favorite.jobTitle(),
                favorite.companyName(),
                favorite.jobDescription(),
                favorite.sourceUrl(),
                favorite.notes(),
                favorite.createdAt()
        );
    }

    public JobResumeVersionResponse createResumeVersion(JobResumeVersionRequest request) {
        String userId = requireUserId(request.userId());
        Instant now = Instant.now();
        JobResumeVersion version = new JobResumeVersion(
                UUID.randomUUID(),
                userId,
                request.versionName(),
                request.targetRole(),
                request.resumeText(),
                request.notes(),
                now,
                now
        );
        return toResumeVersionResponse(jobResumeVersionRepository.save(version));
    }

    public List<JobResumeVersionResponse> listResumeVersions(String userId) {
        return jobResumeVersionRepository.findByUserIdOrderByUpdatedAtDesc(requireUserId(userId))
                .stream()
                .map(this::toResumeVersionResponse)
                .toList();
    }

    public PageResponse<JobResumeVersionResponse> listResumeVersionsPage(String userId, Pageable pageable) {
        return PageResponse.from(
                jobResumeVersionRepository.findByUserIdOrderByUpdatedAtDesc(requireUserId(userId), pageable),
                this::toResumeVersionResponse
        );
    }

    public JobResumeVersionResponse getResumeVersion(UUID versionId, String userId) {
        return toResumeVersionResponse(getOwnedResumeVersion(versionId, userId));
    }

    public JobResumeVersionResponse updateResumeVersion(UUID versionId, JobResumeVersionRequest request) {
        JobResumeVersion version = getOwnedResumeVersion(versionId, requireUserId(request.userId()));
        version.update(
                request.versionName(),
                request.targetRole(),
                request.resumeText(),
                request.notes(),
                Instant.now()
        );
        return toResumeVersionResponse(jobResumeVersionRepository.save(version));
    }

    public void deleteResumeVersion(UUID versionId, String userId) {
        JobResumeVersion version = getOwnedResumeVersion(versionId, userId);
        jobResumeVersionRepository.delete(version);
    }

    private JobResumeVersion getOwnedResumeVersion(UUID versionId, String userId) {
        String requiredUserId = requireUserId(userId);
        JobResumeVersion version = jobResumeVersionRepository.findById(versionId)
                .orElseThrow(() -> new BusinessException("简历版本不存在: " + versionId));
        if (!version.userId().equals(requiredUserId)) {
            throw new ForbiddenException("无权访问该简历版本: " + versionId);
        }

        return version;
    }

    private JobResumeVersionResponse toResumeVersionResponse(JobResumeVersion version) {
        return new JobResumeVersionResponse(
                version.id(),
                version.userId(),
                version.versionName(),
                version.targetRole(),
                version.resumeText(),
                version.notes(),
                version.createdAt(),
                version.updatedAt()
        );
    }

}
