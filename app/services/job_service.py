"""求职辅助 Agent 服务。

这个文件负责第一版“简历 + JD 匹配分析”逻辑：
- 接收简历文本和岗位 JD。
- 构建发送给 qwen-plus 的 messages。
- 要求模型只返回 JSON。
- 把模型返回解析成 JobAnalyzeResponse。

当前版本暂时不使用 Qdrant，因为第一版输入文本较短，可以直接放进 Prompt。
后续如果接入岗位库、面经库或多份简历检索，再把 RAG 能力接进来。
"""

import json
from json import JSONDecodeError
from textwrap import dedent

from pydantic import ValidationError

from app.core.exceptions import BadRequestError
from app.schemas.job import JobAnalyzeRequest, JobAnalyzeResponse
from app.services.qwen_service import chat_completion


def analyze_job_match(request: JobAnalyzeRequest) -> JobAnalyzeResponse:
    """分析简历和 JD 的匹配情况。

    参数：
    - request.resume_text：用户粘贴的简历文本。
    - request.job_description：用户粘贴的岗位 JD。

    返回：
    - JobAnalyzeResponse：匹配分数、已匹配技能、缺失技能、优势、风险、建议和面试题。
    """
    if not request.resume_text.strip():
        raise BadRequestError("简历内容不能为空")

    if not request.job_description.strip():
        raise BadRequestError("岗位 JD 不能为空")

    messages = build_job_analyze_messages(request)
    raw_answer = chat_completion(messages)

    return parse_job_analyze_response(raw_answer)


def build_job_analyze_messages(request: JobAnalyzeRequest) -> list[dict]:
    """构建求职分析 Prompt。

    关键变量：
    - system_prompt：告诉模型角色、任务和输出格式。
    - user_prompt：放入真实的简历文本和岗位 JD。
    - messages：OpenAI-compatible Chat 消息列表。
    """
    system_prompt = dedent(
        """
        你是一个求职匹配分析助手。
        请根据用户提供的简历和岗位 JD，分析候选人与岗位的匹配情况。
        你必须只输出 JSON，不要输出 Markdown，不要输出额外解释。

        JSON 字段必须包含：
        match_score, matched_skills, missing_skills, strengths, risks, suggestions, interview_questions

        字段类型要求：
        - match_score: 0 到 100 的整数
        - matched_skills: 字符串数组
        - missing_skills: 字符串数组
        - strengths: 字符串数组
        - risks: 字符串数组
        - suggestions: 字符串数组
        - interview_questions: 字符串数组

        输出示例：
        {
          "match_score": 80,
          "matched_skills": ["Spring Boot", "MySQL"],
          "missing_skills": ["Redis"],
          "strengths": ["有完整项目经验"],
          "risks": ["缺少生产部署经验"],
          "suggestions": ["补充 Redis 使用场景"],
          "interview_questions": ["你如何设计权限控制？"]
        }
        """
    ).strip()

    user_prompt = dedent(
        f"""
        简历内容：
        {request.resume_text.strip()}

        岗位 JD：
        {request.job_description.strip()}
        """
    ).strip()

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]


def parse_job_analyze_response(raw_answer: str) -> JobAnalyzeResponse:
    """把模型返回解析成 JobAnalyzeResponse。

    模型有时会把 JSON 包在 ```json 代码块里，所以这里不直接 json.loads(raw_answer)，
    而是截取第一个 { 到最后一个 } 之间的内容再解析。
    """
    json_text = extract_json_object(raw_answer)

    try:
        data = json.loads(json_text)
    except JSONDecodeError as error:
        raise BadRequestError(
            "模型返回的求职分析结果不是合法 JSON",
            details={"raw_answer": raw_answer[:500]},
        ) from error

    try:
        return JobAnalyzeResponse(**data)
    except ValidationError as error:
        raise BadRequestError(
            "模型返回的求职分析字段不符合要求",
            details={"errors": error.errors()},
        ) from error


def extract_json_object(text: str) -> str:
    """从模型文本中提取 JSON 对象字符串。"""
    start = text.find("{")
    end = text.rfind("}")

    if start == -1 or end == -1 or end <= start:
        raise BadRequestError(
            "模型没有返回 JSON 对象",
            details={"raw_answer": text[:500]},
        )

    return text[start:end + 1]
