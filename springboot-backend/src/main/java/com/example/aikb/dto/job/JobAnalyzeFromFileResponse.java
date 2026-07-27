package com.example.aikb.dto.job;

import com.example.aikb.dto.fastapi.FastApiJobAnalyzeResponse;

import java.util.List;

/**
 * 岗位附件识别与匹配分析的组合响应。
 *
 * 除分析结果外必须把 OCR/文档解析得到的 JD 文本返回前端，供后续简历优化、
 * 面试准备和求职成品包继续复用。
 */
public record JobAnalyzeFromFileResponse(
        FastApiJobAnalyzeResponse analysis,
        String extractedJobDescription,
        String filename,
        String sourceType,
        List<String> warnings
) {
}
