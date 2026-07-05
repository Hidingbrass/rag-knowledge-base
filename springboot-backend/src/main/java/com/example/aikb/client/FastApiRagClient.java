package com.example.aikb.client;

import com.example.aikb.config.FastApiProperties;
import com.example.aikb.dto.fastapi.FastApiDocumentIndexResponse;
import com.example.aikb.dto.fastapi.FastApiInterviewPrepRequest;
import com.example.aikb.dto.fastapi.FastApiInterviewPrepResponse;
import com.example.aikb.dto.fastapi.FastApiJdParseRequest;
import com.example.aikb.dto.fastapi.FastApiJdParseResponse;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeRequest;
import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;
import com.example.aikb.dto.fastapi.FastApiRagResponse;
import com.example.aikb.dto.fastapi.FastApiRerankChatRequest;
import com.example.aikb.dto.fastapi.FastApiResumeOptimizeRequest;
import com.example.aikb.dto.fastapi.FastApiResumeOptimizeResponse;
import com.example.aikb.dto.fastapi.FastApiResumeParseRequest;
import com.example.aikb.dto.fastapi.FastApiResumeParseResponse;
import com.example.aikb.dto.fastapi.FastApiStarInterviewAnswerRequest;
import com.example.aikb.dto.fastapi.FastApiStarInterviewAnswerResponse;
import com.example.aikb.exception.BusinessException;
import com.example.aikb.service.AiCallLogService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * FastAPI AI 服务客户端。
 * <p>
 * Client 层只负责“怎么调用外部服务”：
 * - 不处理 Controller 参数校验；
 * - 不保存数据库；
 * - 不决定文档状态；
 * - 只把 Java 请求转换成 FastAPI 能理解的 HTTP 请求。
 * <p>
 * 这样后续 FastAPI 路径、字段、超时策略变化时，只需要集中修改这里。
 */
@Component
public class FastApiRagClient {

    private final RestClient fastApiRestClient;
    private final FastApiProperties properties;
    private final AiCallLogService aiCallLogService;

    public FastApiRagClient(
            RestClient fastApiRestClient,
            FastApiProperties properties,
            AiCallLogService aiCallLogService
    ) {
        this.fastApiRestClient = fastApiRestClient;
        this.properties = properties;
        this.aiCallLogService = aiCallLogService;
    }

    /**
     * 调用 FastAPI 的 Rerank RAG 问答接口。
     * <p>
     * 当前使用 application.yml 里的默认参数：
     * - candidateK：向量/混合检索阶段召回的候选片段数量；
     * - rerankTopK：Rerank 后进入 Prompt 的片段数量；
     * - rerankMinScore：最高 rerank_score 低于这个分数时拒答；
     * - retrievalMode：vector 或 hybrid；
     * - keywordLimit：hybrid 检索中的关键词候选数量。
     */
    public FastApiRagResponse askWithRerank(String question, String documentId) {
        FastApiRerankChatRequest request = new FastApiRerankChatRequest(
                question,
                properties.defaultCandidateK(),
                properties.defaultRerankTopK(),
                properties.defaultRerankMinScore(),
                properties.defaultRetrievalMode(),
                properties.defaultKeywordLimit(),
                documentId
        );

        return callFastApi("RAG_RERANK_CHAT", "/rag/chat/rerank", () ->
                fastApiRestClient.post()
                    .uri("/rag/chat/rerank")
                    .body(request)
                    .retrieve()
                    .body(FastApiRagResponse.class),
                "调用 FastAPI RAG 服务失败"
        );
    }

    /**
     * 调用 FastAPI 的 PDF 文档入库接口。
     * <p>
     * 输入：
     * - file：Spring Boot 接收到的 MultipartFile。
     * <p>
     * 输出：
     * - FastApiDocumentIndexResponse：FastAPI 返回的 document_id、chunk_count、file_hash 等信息。
     * <p>
     * 为什么这里要重新包装 ByteArrayResource：
     * RestClient 转发 multipart/form-data 时，需要一个资源对象代表文件内容；
     * 同时要保留原始文件名，否则 FastAPI 侧拿到的 filename 可能为空。
     */
    public FastApiDocumentIndexResponse indexDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();

        try {
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            return callFastApi("DOCUMENT_INDEX", "/documents/index", () ->
                    fastApiRestClient.post()
                    .uri("/documents/index")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(FastApiDocumentIndexResponse.class),
                    "调用 FastAPI 文档入库服务失败"
            );
        } catch (IOException exception) {
            throw new BusinessException("读取上传文件失败", exception);
        }
    }

    public FastApiJobAnalyzeResponse analyzeJob(String resumeText, String jobDescription) {
        FastApiJobAnalyzeRequest request = new FastApiJobAnalyzeRequest(
                resumeText,
                jobDescription
        );
        return callFastApi("JOB_ANALYZE", "/job/analyze", () ->
                fastApiRestClient.post()
                    .uri("/job/analyze")
                    .body(request)
                    .retrieve()
                    .body(FastApiJobAnalyzeResponse.class),
                "调用 FastAPI 求职分析服务失败"
        );
    }

    public FastApiResumeParseResponse parseResume(String resumeText) {
        FastApiResumeParseRequest request = new FastApiResumeParseRequest(resumeText);

        return callFastApi("RESUME_PARSE", "/job/resume/parse", () ->
                fastApiRestClient.post()
                    .uri("/job/resume/parse")
                    .body(request)
                    .retrieve()
                    .body(FastApiResumeParseResponse.class),
                "调用 FastAPI 简历结构化服务失败"
        );
    }

    public FastApiJdParseResponse parseJd(String jobDescription) {
        FastApiJdParseRequest request = new FastApiJdParseRequest(jobDescription);

        return callFastApi("JD_PARSE", "/job/jd/parse", () ->
                fastApiRestClient.post()
                    .uri("/job/jd/parse")
                    .body(request)
                    .retrieve()
                    .body(FastApiJdParseResponse.class),
                "调用 FastAPI JD 结构化服务失败"
        );
    }

    public FastApiResumeOptimizeResponse optimizeResume(String resumeText, String jobDescription) {
        FastApiResumeOptimizeRequest request = new FastApiResumeOptimizeRequest(
                resumeText,
                jobDescription
        );

        return callFastApi("RESUME_OPTIMIZE", "/job/resume/optimize", () ->
                fastApiRestClient.post()
                    .uri("/job/resume/optimize")
                    .body(request)
                    .retrieve()
                    .body(FastApiResumeOptimizeResponse.class),
                "调用 FastAPI 简历优化服务失败"
        );
    }

    public FastApiInterviewPrepResponse prepareInterview(String resumeText, String jobDescription) {
        FastApiInterviewPrepRequest request = new FastApiInterviewPrepRequest(
                resumeText,
                jobDescription
        );

        return callFastApi("INTERVIEW_PREP", "/job/interview/prepare", () ->
                fastApiRestClient.post()
                    .uri("/job/interview/prepare")
                    .body(request)
                    .retrieve()
                    .body(FastApiInterviewPrepResponse.class),
                "调用 FastAPI 面试准备服务失败"
        );
    }

    public FastApiStarInterviewAnswerResponse generateStarInterviewAnswer(String resumeText, String jobDescription, String question) {
        FastApiStarInterviewAnswerRequest request = new FastApiStarInterviewAnswerRequest(
                resumeText,
                jobDescription,
                question
        );

        return callFastApi("STAR_INTERVIEW_ANSWER", "/job/interview/star-answer", () ->
                fastApiRestClient.post()
                    .uri("/job/interview/star-answer")
                    .body(request)
                    .retrieve()
                    .body(FastApiStarInterviewAnswerResponse.class),
                "调用 FastAPI STAR 面试答案服务失败"
        );
    }

    private <T> T callFastApi(String businessType, String endpoint, Supplier<T> call, String failureMessage) {
        long startedAt = System.nanoTime();
        try {
            T response = call.get();
            aiCallLogService.recordFastApiCall(businessType, endpoint, true, elapsedMs(startedAt), null);
            return response;
        } catch (RestClientException exception) {
            aiCallLogService.recordFastApiCall(
                    businessType,
                    endpoint,
                    false,
                    elapsedMs(startedAt),
                    exception.getMessage()
            );
            throw new BusinessException(failureMessage, exception);
        }
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }
}
