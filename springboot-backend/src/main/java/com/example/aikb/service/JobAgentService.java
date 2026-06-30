package com.example.aikb.service;

import com.example.aikb.client.FastApiRagClient;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.job.JobAnalysisTaskResponse;
import com.example.aikb.dto.job.JobAnalyzeRequest;
import com.example.aikb.entity.JobAnalysisTask;
import com.example.aikb.exception.BusinessException;
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
 * - 后续可以在这里保存求职分析任务、记录耗时或做权限校验。
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

    public List<JobAnalysisTaskResponse> listTasks(String userId) {
        List<JobAnalysisTask> tasks = jobAnalysisTaskRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return tasks.stream()
                .map(task -> new JobAnalysisTaskResponse(
                        task.id(),
                        task.userId(),
                        task.resumeText(),
                        task.jobDescription(),
                        task.matchScore(),
                        task.resultJson(),
                        task.createdAt()
                ))
                .toList();
    }
}
