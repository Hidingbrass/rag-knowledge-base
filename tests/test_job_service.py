import pytest

from app.core.exceptions import BadRequestError
from app.schemas.job import (
    InterviewPrepRequest,
    JdParseRequest,
    JobAnalyzeRequest,
    JobDeliveryPackageRequest,
    ResumeOptimizeRequest,
    ResumeParseRequest,
    StarInterviewAnswerRequest,
)
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


def test_build_job_delivery_package_messages_contains_resume_and_jd():
    request = JobDeliveryPackageRequest(
        resume_text="我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
        job_description="岗位要求熟悉 Java、Spring Boot、RAG 和大模型应用。",
    )

    messages = job_service.build_job_delivery_package_messages(request)

    assert messages[0]["role"] == "system"
    assert "求职成品包" in messages[0]["content"]
    assert "project_pitch" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "企业知识库 RAG 项目" in messages[1]["content"]
    assert "大模型应用" in messages[1]["content"]


def test_parse_job_delivery_package_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "target_position": "Java 后端开发工程师",
      "self_introduction": "面试官您好，我主要做 Java 后端和 AI 应用。",
      "project_pitch": "我重点介绍企业知识库 RAG 项目。",
      "architecture_talking_points": ["Spring Boot 负责业务层", "FastAPI 负责 AI 服务"],
      "risk_response": ["Redis 经验可以结合限流和防重复提交说明"],
      "closing_statement": "我希望把后端工程能力和 AI 应用落地结合起来。",
      "rehearsal_checklist": ["练熟 RAG 全链路", "准备权限控制细节"]
    }
    ```
    """

    result = job_service.parse_job_delivery_package_response(raw_answer)

    assert result.target_position == "Java 后端开发工程师"
    assert "RAG 项目" in result.project_pitch
    assert result.architecture_talking_points == ["Spring Boot 负责业务层", "FastAPI 负责 AI 服务"]


def test_generate_job_delivery_package_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        return """
        {
          "target_position": "AI 应用开发工程师",
          "self_introduction": "我有 RAG 项目经验。",
          "project_pitch": "项目实现了文档入库、向量检索和问答。",
          "architecture_talking_points": ["MySQL 保存业务数据", "Qdrant 保存向量"],
          "risk_response": ["高并发经验可以结合 Redis 限流说明"],
          "closing_statement": "我希望继续做 AI 应用落地。",
          "rehearsal_checklist": ["讲清楚 Rerank", "讲清楚权限边界"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.generate_job_delivery_package(
        JobDeliveryPackageRequest(
            resume_text="RAG 项目",
            job_description="AI 应用岗位",
        )
    )

    assert result.target_position == "AI 应用开发工程师"
    assert "Redis 限流" in result.risk_response[0]


def test_generate_job_delivery_package_rejects_empty_jd():
    with pytest.raises(BadRequestError):
        job_service.generate_job_delivery_package(
            JobDeliveryPackageRequest(
                resume_text="RAG 项目",
                job_description="   ",
            )
        )


def test_build_resume_parse_messages_contains_resume_and_schema_fields():
    request = ResumeParseRequest(
        resume_text="我做过企业知识库 RAG 项目，技术栈包括 Spring Boot、FastAPI、MySQL 和 Qdrant。"
    )

    messages = job_service.build_resume_parse_messages(request)

    assert messages[0]["role"] == "system"
    assert "只输出 JSON" in messages[0]["content"]
    assert "target_roles" in messages[0]["content"]
    assert "projects 每一项必须包含" in messages[0]["content"]
    assert "tech_stack" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "企业知识库 RAG 项目" in messages[1]["content"]


def test_parse_resume_parse_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "target_roles": ["Java 后端开发", "AI 应用开发"],
      "skills": ["Java", "Spring Boot", "Python", "FastAPI", "MySQL", "Qdrant", "RAG"],
      "projects": [
        {
          "name": "企业智能知识库 RAG 问答系统",
          "role": "后端开发",
          "tech_stack": ["Spring Boot", "FastAPI", "MySQL", "Qdrant"],
          "description": "实现企业文档入库、向量检索、RAG 问答和权限控制。",
          "highlights": ["完成端到端 RAG 链路", "实现聊天记录持久化"]
        }
      ],
      "work_experiences": [],
      "education": [],
      "certifications": [],
      "strengths": ["具备端到端项目落地经验"],
      "keywords": ["RAG", "向量数据库", "通义千问"]
    }
    ```
    """

    result = job_service.parse_resume_parse_response(raw_answer)

    assert result.target_roles == ["Java 后端开发", "AI 应用开发"]
    assert "Qdrant" in result.skills
    assert result.projects[0].name == "企业智能知识库 RAG 问答系统"
    assert result.projects[0].tech_stack == ["Spring Boot", "FastAPI", "MySQL", "Qdrant"]
    assert result.keywords == ["RAG", "向量数据库", "通义千问"]


def test_parse_resume_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "FastAPI RAG 项目" in messages[1]["content"]
        return """
        {
          "target_roles": ["AI 应用开发"],
          "skills": ["Python", "FastAPI", "RAG"],
          "projects": [
            {
              "name": "RAG 问答系统",
              "role": "后端开发",
              "tech_stack": ["FastAPI", "Qdrant"],
              "description": "完成文档检索和问答链路。",
              "highlights": ["接入向量数据库"]
            }
          ],
          "work_experiences": [],
          "education": [],
          "certifications": [],
          "strengths": ["有 AI 项目经验"],
          "keywords": ["FastAPI", "RAG"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.parse_resume(
        ResumeParseRequest(resume_text="我做过 FastAPI RAG 项目。")
    )

    assert result.target_roles == ["AI 应用开发"]
    assert result.projects[0].name == "RAG 问答系统"
    assert result.strengths == ["有 AI 项目经验"]


def test_parse_resume_rejects_empty_resume():
    with pytest.raises(BadRequestError):
        job_service.parse_resume(ResumeParseRequest(resume_text="   "))


def test_build_jd_parse_messages_contains_jd_and_schema_fields():
    request = JdParseRequest(
        job_description="岗位要求熟悉 Java、Spring Boot、MySQL，有 RAG 项目经验优先。"
    )

    messages = job_service.build_jd_parse_messages(request)

    assert messages[0]["role"] == "system"
    assert "只输出 JSON" in messages[0]["content"]
    assert "required_skills" in messages[0]["content"]
    assert "preferred_skills" in messages[0]["content"]
    assert "responsibilities" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "RAG 项目经验优先" in messages[1]["content"]


def test_parse_jd_parse_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "job_title": "Java 后端开发工程师",
      "seniority": "中级",
      "required_skills": ["Java", "Spring Boot", "MySQL"],
      "preferred_skills": ["FastAPI", "RAG", "向量数据库"],
      "responsibilities": ["负责后端接口开发", "参与 AI 应用落地"],
      "requirements": ["熟悉 Java 技术栈", "具备数据库设计能力"],
      "keywords": ["Java", "Spring Boot", "RAG"],
      "risks": ["可能要求生产环境部署经验"]
    }
    ```
    """

    result = job_service.parse_jd_parse_response(raw_answer)

    assert result.job_title == "Java 后端开发工程师"
    assert result.seniority == "中级"
    assert result.required_skills == ["Java", "Spring Boot", "MySQL"]
    assert result.preferred_skills == ["FastAPI", "RAG", "向量数据库"]
    assert result.risks == ["可能要求生产环境部署经验"]


def test_parse_jd_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "Spring Boot" in messages[1]["content"]
        return """
        {
          "job_title": "AI 应用开发工程师",
          "seniority": "中级",
          "required_skills": ["Python", "FastAPI"],
          "preferred_skills": ["RAG", "Qdrant"],
          "responsibilities": ["建设 AI 应用服务"],
          "requirements": ["熟悉 Web API 开发"],
          "keywords": ["FastAPI", "RAG"],
          "risks": ["需要说明大模型调用经验"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.parse_jd(
        JdParseRequest(job_description="需要 Spring Boot、FastAPI 和 RAG 项目经验。")
    )

    assert result.job_title == "AI 应用开发工程师"
    assert result.required_skills == ["Python", "FastAPI"]
    assert result.keywords == ["FastAPI", "RAG"]


def test_parse_jd_rejects_empty_jd():
    with pytest.raises(BadRequestError):
        job_service.parse_jd(JdParseRequest(job_description="   "))


def test_build_resume_optimize_messages_contains_resume_jd_and_schema_fields():
    request = ResumeOptimizeRequest(
        resume_text="我做过 Spring Boot + FastAPI RAG 项目。",
        job_description="岗位要求 Java、Spring Boot、RAG、向量数据库。",
    )

    messages = job_service.build_resume_optimize_messages(request)

    assert messages[0]["role"] == "system"
    assert "只输出 JSON" in messages[0]["content"]
    assert "rewrite_suggestions" in messages[0]["content"]
    assert "after_text" in messages[0]["content"]
    assert "不要编造用户没有做过的经历" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "Spring Boot + FastAPI RAG 项目" in messages[1]["content"]
    assert "岗位要求 Java、Spring Boot、RAG" in messages[1]["content"]


def test_parse_resume_optimize_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "summary": "强化 RAG 项目和 Java 后端能力表达。",
      "target_position": "Java 后端开发工程师",
      "gap_summary": ["简历中生产环境经验体现不足"],
      "rewrite_suggestions": [
        {
          "section": "项目经历",
          "issue": "项目成果表达不够贴近 JD",
          "suggestion": "突出 Spring Boot、FastAPI、Qdrant 和大模型调用链路",
          "before_text": "我做过 RAG 项目。",
          "after_text": "基于 Spring Boot + FastAPI 构建企业知识库 RAG 系统，完成 PDF 入库、Embedding、Qdrant 检索、Rerank 和问答链路。",
          "keywords_added": ["Spring Boot", "FastAPI", "Qdrant", "RAG"]
        }
      ],
      "missing_keywords": ["Redis"],
      "action_items": ["准备说明接口容错和部署方案"]
    }
    ```
    """

    result = job_service.parse_resume_optimize_response(raw_answer)

    assert result.target_position == "Java 后端开发工程师"
    assert result.gap_summary == ["简历中生产环境经验体现不足"]
    assert result.rewrite_suggestions[0].section == "项目经历"
    assert "企业知识库 RAG 系统" in result.rewrite_suggestions[0].after_text
    assert result.missing_keywords == ["Redis"]
    assert result.action_items == ["准备说明接口容错和部署方案"]


def test_optimize_resume_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "FastAPI RAG 项目" in messages[1]["content"]
        return """
        {
          "summary": "突出 AI 应用落地经验。",
          "target_position": "AI 应用开发工程师",
          "gap_summary": ["需要更明确大模型调用经验"],
          "rewrite_suggestions": [
            {
              "section": "项目经历",
              "issue": "AI 能力表达较泛",
              "suggestion": "补充 Embedding、Rerank 和 Chat 调用链路",
              "before_text": "我做过 FastAPI RAG 项目。",
              "after_text": "使用 FastAPI 编排 Embedding、Rerank 和 Chat 调用，完成企业知识库 RAG 问答服务。",
              "keywords_added": ["Embedding", "Rerank", "Chat"]
            }
          ],
          "missing_keywords": ["监控"],
          "action_items": ["补充接口失败重试策略"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.optimize_resume(
        ResumeOptimizeRequest(
            resume_text="我做过 FastAPI RAG 项目。",
            job_description="岗位需要 FastAPI、RAG 和大模型调用经验。",
        )
    )

    assert result.summary == "突出 AI 应用落地经验。"
    assert result.rewrite_suggestions[0].keywords_added == ["Embedding", "Rerank", "Chat"]


def test_optimize_resume_rejects_empty_resume_or_jd():
    with pytest.raises(BadRequestError):
        job_service.optimize_resume(
            ResumeOptimizeRequest(
                resume_text="   ",
                job_description="岗位需要 Java。",
            )
        )

    with pytest.raises(BadRequestError):
        job_service.optimize_resume(
            ResumeOptimizeRequest(
                resume_text="我做过 Java 项目。",
                job_description="   ",
            )
        )


def test_build_interview_prep_messages_contains_resume_jd_and_schema_fields():
    request = InterviewPrepRequest(
        resume_text="我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
        job_description="岗位要求 Java、Spring Boot、RAG、向量数据库。",
    )

    messages = job_service.build_interview_prep_messages(request)

    assert messages[0]["role"] == "system"
    assert "只输出 JSON" in messages[0]["content"]
    assert "self_introduction" in messages[0]["content"]
    assert "project_talking_points" in messages[0]["content"]
    assert "technical_questions" in messages[0]["content"]
    assert "不要编造用户没有做过的项目经历" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "企业知识库 RAG 项目" in messages[1]["content"]
    assert "岗位要求 Java、Spring Boot、RAG" in messages[1]["content"]


def test_parse_interview_prep_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "target_position": "Java 后端开发工程师",
      "self_introduction": "面试官您好，我主要使用 Java 和 Python 做 AI 应用开发。",
      "project_talking_points": [
        {
          "project_name": "企业知识库 RAG 系统",
          "pitch": "我负责 Spring Boot 业务后端和 FastAPI AI 服务联调。",
          "technical_depth": ["文档切分策略", "Qdrant 向量检索", "Rerank 拒答阈值"],
          "likely_followups": ["Qdrant 和 MySQL 如何分工？"]
        }
      ],
      "technical_questions": [
        {
          "question": "RAG 中如何减少幻觉？",
          "answer_points": ["引用来源", "无依据拒答", "Rerank 重排"]
        }
      ],
      "behavioral_questions": [
        {
          "question": "项目中遇到过什么困难？",
          "answer_points": ["描述问题", "说明排查过程", "总结结果"]
        }
      ],
      "questions_to_ask": ["团队目前 AI 应用主要落在哪些业务场景？"],
      "preparation_checklist": ["复习 RAG 链路", "准备项目架构图"]
    }
    ```
    """

    result = job_service.parse_interview_prep_response(raw_answer)

    assert result.target_position == "Java 后端开发工程师"
    assert "Java 和 Python" in result.self_introduction
    assert result.project_talking_points[0].project_name == "企业知识库 RAG 系统"
    assert result.technical_questions[0].question == "RAG 中如何减少幻觉？"
    assert result.behavioral_questions[0].answer_points == ["描述问题", "说明排查过程", "总结结果"]
    assert result.questions_to_ask == ["团队目前 AI 应用主要落在哪些业务场景？"]


def test_prepare_interview_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "FastAPI RAG 项目" in messages[1]["content"]
        return """
        {
          "target_position": "AI 应用开发工程师",
          "self_introduction": "面试官您好，我做过 FastAPI RAG 项目。",
          "project_talking_points": [
            {
              "project_name": "RAG 问答系统",
              "pitch": "我负责模型调用和向量检索链路。",
              "technical_depth": ["Embedding", "Rerank"],
              "likely_followups": ["为什么需要 Rerank？"]
            }
          ],
          "technical_questions": [
            {
              "question": "Embedding 在系统中起什么作用？",
              "answer_points": ["把文本转成向量", "用于语义检索"]
            }
          ],
          "behavioral_questions": [
            {
              "question": "如何推进跨技术栈联调？",
              "answer_points": ["定义接口契约", "先 Mock 再联调"]
            }
          ],
          "questions_to_ask": ["团队如何评估 AI 应用效果？"],
          "preparation_checklist": ["准备 RAG 项目讲解"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.prepare_interview(
        InterviewPrepRequest(
            resume_text="我做过 FastAPI RAG 项目。",
            job_description="岗位需要 FastAPI、RAG 和大模型调用经验。",
        )
    )

    assert result.target_position == "AI 应用开发工程师"
    assert result.project_talking_points[0].technical_depth == ["Embedding", "Rerank"]
    assert result.technical_questions[0].answer_points == ["把文本转成向量", "用于语义检索"]


def test_prepare_interview_rejects_empty_resume_or_jd():
    with pytest.raises(BadRequestError):
        job_service.prepare_interview(
            InterviewPrepRequest(
                resume_text="   ",
                job_description="岗位需要 Java。",
            )
        )

    with pytest.raises(BadRequestError):
        job_service.prepare_interview(
            InterviewPrepRequest(
                resume_text="我做过 Java 项目。",
                job_description="   ",
            )
        )


def test_build_star_interview_answer_messages_contains_question_and_schema_fields():
    request = StarInterviewAnswerRequest(
        resume_text="我做过 Spring Boot + FastAPI 企业知识库 RAG 项目。",
        job_description="岗位要求 Java、Spring Boot、RAG、向量数据库。",
        question="请讲一下你在项目中遇到的最大困难。",
    )

    messages = job_service.build_star_interview_answer_messages(request)

    assert messages[0]["role"] == "system"
    assert "只输出 JSON" in messages[0]["content"]
    assert "situation" in messages[0]["content"]
    assert "follow_up_questions" in messages[0]["content"]
    assert "不要编造用户没有做过的项目经历" in messages[0]["content"]
    assert messages[1]["role"] == "user"
    assert "企业知识库 RAG 项目" in messages[1]["content"]
    assert "岗位要求 Java、Spring Boot、RAG" in messages[1]["content"]
    assert "最大困难" in messages[1]["content"]


def test_parse_star_interview_answer_response_accepts_json_code_block():
    raw_answer = """
    ```json
    {
      "target_position": "Java 后端开发工程师",
      "question": "请讲一下你在项目中遇到的最大困难。",
      "situation": "在企业知识库 RAG 项目中，需要同时打通 Spring Boot 业务后端和 FastAPI AI 服务。",
      "task": "我负责把文档入库、向量检索、Rerank 和问答结果稳定串起来。",
      "action": ["先定义接口契约", "使用 Mock 测试拆分问题", "补充异常处理和状态记录"],
      "result": "最终完成从 PDF 入库到带引用问答的端到端链路。",
      "answer": "我可以用 RAG 项目举例。当时的背景是...",
      "highlights": ["跨技术栈联调", "问题拆解", "端到端交付"],
      "follow_up_questions": ["FastAPI 调用失败时如何处理？"]
    }
    ```
    """

    result = job_service.parse_star_interview_answer_response(raw_answer)

    assert result.target_position == "Java 后端开发工程师"
    assert result.question == "请讲一下你在项目中遇到的最大困难。"
    assert "Spring Boot 业务后端" in result.situation
    assert result.action == ["先定义接口契约", "使用 Mock 测试拆分问题", "补充异常处理和状态记录"]
    assert result.highlights == ["跨技术栈联调", "问题拆解", "端到端交付"]
    assert result.follow_up_questions == ["FastAPI 调用失败时如何处理？"]


def test_generate_star_interview_answer_calls_chat_completion(monkeypatch):
    def fake_chat_completion(messages):
        assert messages[0]["role"] == "system"
        assert messages[1]["role"] == "user"
        assert "FastAPI RAG 项目" in messages[1]["content"]
        assert "如何解决幻觉问题" in messages[1]["content"]
        return """
        {
          "target_position": "AI 应用开发工程师",
          "question": "RAG 中如何解决幻觉问题？",
          "situation": "项目需要回答企业文档问题，并避免没有依据的回答。",
          "task": "我需要让系统基于检索片段回答，并在依据不足时拒答。",
          "action": ["保留引用来源", "接入 Rerank", "设置拒答阈值"],
          "result": "系统可以返回带来源的答案，并在相关性不足时拒答。",
          "answer": "在我的 RAG 项目中，幻觉控制主要从三层做...",
          "highlights": ["RAG 工程实践", "效果控制", "可解释性"],
          "follow_up_questions": ["拒答阈值如何确定？"]
        }
        """

    monkeypatch.setattr(job_service, "chat_completion", fake_chat_completion)

    result = job_service.generate_star_interview_answer(
        StarInterviewAnswerRequest(
            resume_text="我做过 FastAPI RAG 项目。",
            job_description="岗位需要 RAG 和大模型调用经验。",
            question="RAG 中如何解决幻觉问题？",
        )
    )

    assert result.target_position == "AI 应用开发工程师"
    assert result.action == ["保留引用来源", "接入 Rerank", "设置拒答阈值"]
    assert result.follow_up_questions == ["拒答阈值如何确定？"]


def test_generate_star_interview_answer_rejects_empty_inputs():
    with pytest.raises(BadRequestError):
        job_service.generate_star_interview_answer(
            StarInterviewAnswerRequest(
                resume_text="   ",
                job_description="岗位需要 Java。",
                question="介绍一个项目难点。",
            )
        )

    with pytest.raises(BadRequestError):
        job_service.generate_star_interview_answer(
            StarInterviewAnswerRequest(
                resume_text="我做过 Java 项目。",
                job_description="   ",
                question="介绍一个项目难点。",
            )
        )

    with pytest.raises(BadRequestError):
        job_service.generate_star_interview_answer(
            StarInterviewAnswerRequest(
                resume_text="我做过 Java 项目。",
                job_description="岗位需要 Java。",
                question="   ",
            )
        )
