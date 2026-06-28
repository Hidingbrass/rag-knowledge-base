import pytest

from app.core.exceptions import BadRequestError
from app.schemas.job import JobAnalyzeRequest
from app.services import job_service


def test_build_job_analyze_messages_contains_resume_and_jd():
    request = JobAnalyzeRequest(
        resume_text="我熟悉 Spring Boot 和 MySQL。",
        job_description="岗位要求：Java、Spring Boot、Redis。",
    )

    messages = job_service.build_job_analyze_messages(request)

    assert messages[0]["role"] == "system"
    assert "只输出 JSON" in messages[0]["content"]
    assert "match_score" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "我熟悉 Spring Boot 和 MySQL。" in messages[1]["content"]
    assert "岗位要求：Java、Spring Boot、Redis。" in messages[1]["content"]


def test_parse_job_analyze_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "match_score": 85,
      "matched_skills": ["Spring Boot", "MySQL"],
      "missing_skills": ["Redis"],
      "strengths": ["项目链路完整"],
      "risks": ["缓存经验体现较少"],
      "suggestions": ["补充 Redis 使用场景"],
      "interview_questions": ["你如何设计文档重复检测？"]
    }
    ```
    """

    result = job_service.parse_job_analyze_response(raw_answer)

    assert result.match_score == 85
    assert result.matched_skills == ["Spring Boot", "MySQL"]
    assert result.missing_skills == ["Redis"]
    assert result.interview_questions == ["你如何设计文档重复检测？"]


def test_analyze_job_match_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        return """
        {
          "match_score": 90,
          "matched_skills": ["FastAPI"],
          "missing_skills": ["Kubernetes"],
          "strengths": ["有 RAG 项目经验"],
          "risks": ["部署经验不足"],
          "suggestions": ["补充容器化部署说明"],
          "interview_questions": ["Rerank 解决了什么问题？"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.analyze_job_match(
        JobAnalyzeRequest(
            resume_text="FastAPI RAG 项目",
            job_description="需要 FastAPI 和 Kubernetes",
        )
    )

    assert result.match_score == 90
    assert result.strengths == ["有 RAG 项目经验"]


def test_analyze_job_match_rejects_empty_resume():
    with pytest.raises(BadRequestError):
        job_service.analyze_job_match(
            JobAnalyzeRequest(
                resume_text="   ",
                job_description="需要 Java 和 Spring Boot",
            )
        )
