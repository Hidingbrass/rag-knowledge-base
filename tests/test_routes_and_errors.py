"""API 路由和统一异常的回归测试。

这个文件主要验证 HTTP 层是否保持稳定：
- main.py 拆分后，原来的接口路径仍然存在。
- 全局异常处理器能把业务异常转换成统一 JSON 响应。

这些测试不调用真实大模型，也不连接真实 Qdrant。
它们只测试 FastAPI app 的路由注册和参数校验结果。
"""

from fastapi.testclient import TestClient

from app.api import job as job_api
from app.core.config import settings
from app.main import app
from app.schemas.job import (
    InterviewPrepResponse,
    JdParseResponse,
    JobAnalyzeResponse,
    JobAttachmentTextResponse,
    ResumeOptimizeResponse,
    ResumeParseResponse,
)


client = TestClient(app, headers={"X-API-Key": settings.fastapi_api_key})


def test_original_routes_are_registered():
    """确认工程化拆分后，原有核心接口路径没有丢失。"""
    route_paths = {
        route.path
        for route in app.routes
        if hasattr(route, "path")
    }

    expected_paths = {
        "/health",
        "/qdrant/health",
        "/chat",
        "/documents/preview",
        "/documents/index",
        "/documents/{document_id}",
        "/documents",
        "/search/demo",
        "/embedding/test",
        "/rag/chat",
        "/rag/chat/rerank",
        "/rag/chat/rerank/stream",
        "/job/analyze",
        "/job/analyze-from-file",
        "/job/resume/parse",
        "/job/jd/parse",
        "/job/jd/extract-text",
        "/job/resume/optimize",
        "/job/interview/prepare",
        "/job/interview/star-answer",
        "/job/delivery-package",
    }

    assert expected_paths.issubset(route_paths)


def test_job_analyze_route_calls_service(monkeypatch):
    """求职分析路由应该接收请求体，并把结果按 JobAnalyzeResponse 返回。"""
    captured = {}

    def fake_analyze_job_match(request):
        captured["resume_text"] = request.resume_text
        captured["job_description"] = request.job_description
        return JobAnalyzeResponse(
            match_score=88,
            matched_skills=["FastAPI", "RAG"],
            missing_skills=["Redis"],
            strengths=["项目链路完整"],
            risks=["缓存经验体现较少"],
            suggestions=["补充 Redis 使用场景"],
            interview_questions=["Rerank 解决了什么问题？"],
        )

    monkeypatch.setattr(job_api, "analyze_job_match", fake_analyze_job_match)

    response = client.post(
        "/job/analyze",
        json={
            "resume_text": "我做过 FastAPI RAG 项目。",
            "job_description": "岗位要求 FastAPI、RAG、Redis。",
        },
    )

    assert response.status_code == 200
    assert captured == {
        "resume_text": "我做过 FastAPI RAG 项目。",
        "job_description": "岗位要求 FastAPI、RAG、Redis。",
    }
    assert response.json() == {
        "match_score": 88,
        "matched_skills": ["FastAPI", "RAG"],
        "missing_skills": ["Redis"],
        "strengths": ["项目链路完整"],
        "risks": ["缓存经验体现较少"],
        "suggestions": ["补充 Redis 使用场景"],
        "interview_questions": ["Rerank 解决了什么问题？"],
        "model_usage": {
            "models": [],
            "upstream_call_count": 0,
            "retry_count": 0,
            "prompt_tokens": 0,
            "completion_tokens": 0,
            "total_tokens": 0,
            "estimated_cost_yuan": 0.0,
        },
    }


def test_resume_parse_route_calls_service(monkeypatch):
    """简历结构化路由应该接收简历文本，并把结构化结果返回给调用方。"""
    captured = {}

    def fake_parse_resume(request):
        captured["resume_text"] = request.resume_text
        return ResumeParseResponse(
            target_roles=["Java 后端开发", "AI 应用开发"],
            skills=["Java", "Spring Boot", "FastAPI", "RAG"],
            projects=[
                {
                    "name": "企业知识库 RAG 系统",
                    "role": "后端开发",
                    "tech_stack": ["Spring Boot", "FastAPI", "Qdrant"],
                    "description": "实现文档入库、向量检索和问答链路。",
                    "highlights": ["完成端到端 RAG 链路"],
                }
            ],
            work_experiences=[],
            education=[],
            certifications=[],
            strengths=["具备端到端项目经验"],
            keywords=["RAG", "向量数据库"],
        )

    monkeypatch.setattr(job_api, "parse_resume", fake_parse_resume)

    response = client.post(
        "/job/resume/parse",
        json={
            "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。"
        },
    )

    assert response.status_code == 200
    assert captured == {
        "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。"
    }
    assert response.json()["target_roles"] == ["Java 后端开发", "AI 应用开发"]
    assert response.json()["projects"][0]["name"] == "企业知识库 RAG 系统"
    assert response.json()["keywords"] == ["RAG", "向量数据库"]


def test_jd_parse_route_calls_service(monkeypatch):
    """JD 结构化路由应该接收岗位描述，并返回岗位结构化结果。"""
    captured = {}

    def fake_parse_jd(request):
        captured["job_description"] = request.job_description
        return JdParseResponse(
            job_title="Java 后端开发工程师",
            seniority="中级",
            required_skills=["Java", "Spring Boot", "MySQL"],
            preferred_skills=["RAG", "向量数据库"],
            responsibilities=["负责后端接口开发"],
            requirements=["熟悉 Java 技术栈"],
            keywords=["Java", "Spring Boot", "RAG"],
            risks=["需要说明生产环境经验"],
        )

    monkeypatch.setattr(job_api, "parse_jd", fake_parse_jd)

    response = client.post(
        "/job/jd/parse",
        json={
            "job_description": "岗位要求 Java、Spring Boot、MySQL，有 RAG 项目经验优先。"
        },
    )

    assert response.status_code == 200
    assert captured == {
        "job_description": "岗位要求 Java、Spring Boot、MySQL，有 RAG 项目经验优先。"
    }
    assert response.json()["job_title"] == "Java 后端开发工程师"
    assert response.json()["required_skills"] == ["Java", "Spring Boot", "MySQL"]
    assert response.json()["preferred_skills"] == ["RAG", "向量数据库"]


def test_jd_extract_text_route_calls_service(monkeypatch):
    """岗位附件文字提取路由应该把上传文件交给 service。"""
    captured = {}

    def fake_extract_job_text_from_attachment(filename, content_type, content):
        captured["filename"] = filename
        captured["content_type"] = content_type
        captured["content"] = content
        return JobAttachmentTextResponse(
            filename=filename,
            source_type="image_ocr",
            text="岗位要求：Java、Spring Boot、Redis。",
            warnings=[],
        )

    monkeypatch.setattr(
        job_api,
        "extract_job_text_from_attachment",
        fake_extract_job_text_from_attachment,
    )

    response = client.post(
        "/job/jd/extract-text",
        files={
            "file": ("jd.png", b"fake-image-content", "image/png"),
        },
    )

    assert response.status_code == 200
    assert captured == {
        "filename": "jd.png",
        "content_type": "image/png",
        "content": b"fake-image-content",
    }
    assert response.json() == {
        "filename": "jd.png",
        "source_type": "image_ocr",
        "text": "岗位要求：Java、Spring Boot、Redis。",
        "warnings": [],
    }


def test_analyze_from_file_route_extracts_jd_then_calls_analyze(monkeypatch):
    """上传岗位截图分析时，应先识别 JD，再复用原有匹配分析逻辑。"""
    captured = {}

    def fake_extract_job_text_from_attachment(filename, content_type, content):
        captured["file"] = (filename, content_type, content)
        return JobAttachmentTextResponse(
            filename=filename,
            source_type="image_ocr",
            text="岗位要求：Java、Spring Boot、Redis。",
            warnings=[],
        )

    def fake_analyze_job_match(request):
        captured["resume_text"] = request.resume_text
        captured["job_description"] = request.job_description
        return JobAnalyzeResponse(
            match_score=92,
            matched_skills=["Java", "Spring Boot"],
            missing_skills=["Redis"],
            strengths=["项目经验匹配"],
            risks=["缓存经验体现不足"],
            suggestions=["补充 Redis 限流经验"],
            interview_questions=["Redis 限流怎么做？"],
        )

    monkeypatch.setattr(
        job_api,
        "extract_job_text_from_attachment",
        fake_extract_job_text_from_attachment,
    )
    monkeypatch.setattr(job_api, "analyze_job_match", fake_analyze_job_match)

    response = client.post(
        "/job/analyze-from-file",
        data={"resume_text": "我做过 Spring Boot RAG 项目。"},
        files={
            "file": ("jd.png", b"fake-image-content", "image/png"),
        },
    )

    assert response.status_code == 200
    assert captured["file"] == ("jd.png", "image/png", b"fake-image-content")
    assert captured["resume_text"] == "我做过 Spring Boot RAG 项目。"
    assert captured["job_description"] == "岗位要求：Java、Spring Boot、Redis。"
    assert response.json()["match_score"] == 92
    assert response.json()["missing_skills"] == ["Redis"]


def test_resume_optimize_route_calls_service(monkeypatch):
    """简历优化路由应该接收简历和 JD，并返回可落地的改写建议。"""
    captured = {}

    def fake_optimize_resume(request):
        captured["resume_text"] = request.resume_text
        captured["job_description"] = request.job_description
        return ResumeOptimizeResponse(
            summary="强化 RAG 项目和 Java 后端能力表达。",
            target_position="Java 后端开发工程师",
            gap_summary=["生产环境经验体现不足"],
            rewrite_suggestions=[
                {
                    "section": "项目经历",
                    "issue": "项目成果表达不够贴近 JD",
                    "suggestion": "突出 Spring Boot、FastAPI、Qdrant 和大模型调用链路",
                    "before_text": "我做过 RAG 项目。",
                    "after_text": "基于 Spring Boot + FastAPI 构建企业知识库 RAG 系统。",
                    "keywords_added": ["Spring Boot", "FastAPI", "RAG"],
                }
            ],
            missing_keywords=["Redis"],
            action_items=["准备说明接口容错和部署方案"],
        )

    monkeypatch.setattr(job_api, "optimize_resume", fake_optimize_resume)

    response = client.post(
        "/job/resume/optimize",
        json={
            "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
            "job_description": "岗位要求 Java、Spring Boot、RAG、向量数据库。",
        },
    )

    assert response.status_code == 200
    assert captured == {
        "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
        "job_description": "岗位要求 Java、Spring Boot、RAG、向量数据库。",
    }
    assert response.json()["target_position"] == "Java 后端开发工程师"
    assert response.json()["rewrite_suggestions"][0]["section"] == "项目经历"
    assert response.json()["missing_keywords"] == ["Redis"]


def test_interview_prepare_route_calls_service(monkeypatch):
    """面试准备路由应该接收简历和 JD，并返回结构化面试准备包。"""
    captured = {}

    def fake_prepare_interview(request):
        captured["resume_text"] = request.resume_text
        captured["job_description"] = request.job_description
        return InterviewPrepResponse(
            target_position="Java 后端开发工程师",
            self_introduction="面试官您好，我主要做 Java 后端和 AI 应用开发。",
            project_talking_points=[
                {
                    "project_name": "企业知识库 RAG 系统",
                    "pitch": "我负责 Spring Boot 和 FastAPI 双后端联调。",
                    "technical_depth": ["Qdrant 检索", "Rerank 拒答"],
                    "likely_followups": ["为什么 MySQL 和 Qdrant 要分开？"],
                }
            ],
            technical_questions=[
                {
                    "question": "RAG 如何减少幻觉？",
                    "answer_points": ["引用来源", "无依据拒答"],
                }
            ],
            behavioral_questions=[
                {
                    "question": "遇到接口联调问题怎么办？",
                    "answer_points": ["确认契约", "查看日志"],
                }
            ],
            questions_to_ask=["团队当前 AI 应用的落地场景是什么？"],
            preparation_checklist=["准备 RAG 架构讲解"],
        )

    monkeypatch.setattr(job_api, "prepare_interview", fake_prepare_interview)

    response = client.post(
        "/job/interview/prepare",
        json={
            "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
            "job_description": "岗位要求 Java、Spring Boot、RAG、向量数据库。",
        },
    )

    assert response.status_code == 200
    assert captured == {
        "resume_text": "我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
        "job_description": "岗位要求 Java、Spring Boot、RAG、向量数据库。",
    }
    assert response.json()["target_position"] == "Java 后端开发工程师"
    assert response.json()["project_talking_points"][0]["project_name"] == "企业知识库 RAG 系统"
    assert response.json()["technical_questions"][0]["question"] == "RAG 如何减少幻觉？"


def test_rerank_top_k_greater_than_candidate_k_returns_bad_request():
    """rerank_top_k 不能大于 candidate_k，否则应该返回统一 400 错误。"""
    response = client.post(
        "/rag/chat/rerank",
        json={
            "question": "test question",
            "candidate_k": 2,
            "rerank_top_k": 3,
        },
    )

    assert response.status_code == 400
    assert response.json() == {
        "error_code": "BAD_REQUEST",
        "message": "rerank_top_k 不能大于 candidate_k",
    }


def test_preview_document_accepts_markdown_file():
    """Markdown 应该进入统一预览解析流程。"""
    response = client.post(
        "/documents/preview",
        files={
            "file": ("notes.md", "# RAG\n\n检索增强生成。".encode(), "text/markdown")
        },
    )

    assert response.status_code == 200
    assert response.json()["document_type"] == "MARKDOWN"
    assert response.json()["chunk_count"] == 1


def test_preview_document_rejects_unsupported_file():
    """上传不支持的格式时，documents API 应该返回统一 400 错误。"""
    response = client.post(
        "/documents/preview",
        files={
            "file": ("notes.csv", b"name,value", "text/csv")
        },
    )

    assert response.status_code == 400
    assert response.json() == {
        "error_code": "BAD_REQUEST",
        "message": "支持 PDF、Markdown、Word（DOCX）和 TXT 文件",
    }
