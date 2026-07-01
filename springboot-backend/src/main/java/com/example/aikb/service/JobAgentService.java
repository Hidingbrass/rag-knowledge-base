package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.job.JobAnalysisTaskResponse;
import com.example.aikb.dto.job.JobAnalyzeRequest;
import com.example.aikb.entity.JobAnalysisTask;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.exception.ForbiddenException;
import com.example.aikb.repository.JobAnalysisTaskRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
    private final ObjectMapper objectMapper;

    public JobAgentService(
            FastApiRagClient fastApiRagClient,
            JobAnalysisTaskRepository jobAnalysisTaskRepository,
            ObjectMapper objectMapper
    ) {
        this.fastApiRagClient = fastApiRagClient;
        this.jobAnalysisTaskRepository = jobAnalysisTaskRepository;
        this.objectMapper = objectMapper;
    }

    public FastApiJobAnalyzeResponse analyze(JobAnalyzeRequest request) {
        FastApiJobAnalyzeResponse response = fastApiRagClient.analyzeJob(
                request.resumeText(),
                request.jobDescription()
        );
        String resultJson = toResultJson(response);
        JobAnalysisTask task = new JobAnalysisTask(
                UUID.randomUUID(),
                request.userId(),
                request.resumeText(),
                request.jobDescription(),
                response.matchScore(),
                resultJson,
                Instant.now()
        );
        jobAnalysisTaskRepository.save(task);
        return response;
    }

    private String toResultJson(FastApiJobAnalyzeResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new BusinessException("序列化求职分析结果失败", e);
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
        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return tasks
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public JobAnalysisTaskResponse getTask(UUID taskId, String userId) {
        JobAnalysisTask task = getOwnedTask(taskId, userId);
        return toResponse(task);
    }

    private JobAnalysisTask getOwnedTask(UUID taskId, String userId) {
        JobAnalysisTask task = jobAnalysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("求职分析记录不存在: " + taskId));
        if (!task.userId().equals(userId)) {
            throw new ForbiddenException("无权访问该求职分析记录: " + taskId);
        }

        return task;
    }

    public void deleteTask(UUID taskId, String userId) {
        JobAnalysisTask task = getOwnedTask(taskId, userId);
        jobAnalysisTaskRepository.delete(task);
    }
}
