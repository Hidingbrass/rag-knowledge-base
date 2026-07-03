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
from app.schemas.job import (
    InterviewPrepRequest,
    InterviewPrepResponse,
    JdParseRequest,
    JdParseResponse,
    JobAnalyzeRequest,
    JobAnalyzeResponse,
    ResumeOptimizeRequest,
    ResumeOptimizeResponse,
    ResumeParseRequest,
    ResumeParseResponse,
    StarInterviewAnswerRequest,
    StarInterviewAnswerResponse,
)
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


def build_resume_parse_messages(request: ResumeParseRequest) -> list[dict]:
    system_prompt = dedent(
        """
        你是简历结构化解析助手。
        请把用户简历解析成固定 JSON。
        你必须只输出 JSON，不要输出 Markdown，不要输出额外解释。

        JSON 字段必须包含：
        target_roles, skills, projects, work_experiences, education, certifications, strengths, keywords

        字段类型要求：
        - target_roles: 字符串数组
        - skills: 字符串数组
        - projects: 对象数组
        - work_experiences: 字符串数组
        - education: 字符串数组
        - certifications: 字符串数组
        - strengths: 字符串数组
        - keywords: 字符串数组

        projects 每一项必须包含：
        name, role, tech_stack, description, highlights

        projects 字段类型要求：
        - name: 字符串
        - role: 字符串
        - tech_stack: 字符串数组
        - description: 字符串
        - highlights: 字符串数组

        如果某个字段无法从简历中识别，请返回空数组，不要省略字段。
        """
    ).strip()

    user_prompt = dedent(
        f"""
        简历内容：
        {request.resume_text.strip()}
        """
    ).strip()

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]


def parse_resume_parse_response(raw_answer: str) -> ResumeParseResponse:
    json_text = extract_json_object(raw_answer)
    try:
        data = json.loads(json_text)
    except JSONDecodeError as error:
        raise BadRequestError(
            "模型返回的简历结构化结果不是合法 JSON",
            details={"raw_answer": raw_answer[:500]},
        ) from error
    try:
        return ResumeParseResponse(**data)
    except ValidationError as error:
        raise BadRequestError(
            "模型返回的简历结构化字段不符合要求",
            details={"errors": error.errors()},
        ) from error


def parse_resume(request: ResumeParseRequest) -> ResumeParseResponse:
    if not request.resume_text.strip():
        raise BadRequestError("简历内容不能为空")

    messages = build_resume_parse_messages(request)
    raw_answer = chat_completion(messages)

    return parse_resume_parse_response(raw_answer)


def build_jd_parse_messages(request: JdParseRequest) -> list[dict]:
    system_prompt = dedent(
        """
        你是岗位 JD 结构化解析助手。
        请把用户提供的岗位描述解析成固定 JSON。
        你必须只输出 JSON，不要输出 Markdown，不要输出额外解释。

        JSON 字段必须包含：
        job_title, seniority, required_skills, preferred_skills, responsibilities, requirements, keywords, risks

        字段类型要求：
        - job_title: 字符串
        - seniority: 字符串，用于概括岗位级别，例如 初级 / 中级 / 高级 / 实习 / 未识别
        - required_skills: 字符串数组，岗位硬性要求技能
        - preferred_skills: 字符串数组，加分项技能
        - responsibilities: 字符串数组，岗位职责
        - requirements: 字符串数组，候选人要求
        - keywords: 字符串数组，适合用于检索和匹配的关键词
        - risks: 字符串数组，候选人需要注意的风险点或隐含要求

        如果某个字段无法从 JD 中识别，请返回空数组或“未识别”，不要省略字段。
        """
    ).strip()

    user_prompt = dedent(
        f"""
        岗位 JD：
        {request.job_description.strip()}
        """
    ).strip()

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]


def parse_jd_parse_response(raw_answer: str) -> JdParseResponse:
    json_text = extract_json_object(raw_answer)
    try:
        data = json.loads(json_text)
    except JSONDecodeError as error:
        raise BadRequestError(
            "模型返回的 JD 结构化结果不是合法 JSON",
            details={"raw_answer": raw_answer[:500]},
        ) from error
    try:
        return JdParseResponse(**data)
    except ValidationError as error:
        raise BadRequestError(
            "模型返回的 JD 结构化字段不符合要求",
            details={"errors": error.errors()},
        ) from error


def parse_jd(request: JdParseRequest) -> JdParseResponse:
    if not request.job_description.strip():
        raise BadRequestError("岗位 JD 不能为空")

    messages = build_jd_parse_messages(request)
    raw_answer = chat_completion(messages)

    return parse_jd_parse_response(raw_answer)


def build_resume_optimize_messages(request: ResumeOptimizeRequest) -> list[dict]:
    system_prompt = dedent(
        """
        你是简历优化顾问。
        请根据用户提供的简历文本和岗位 JD，生成更匹配该岗位的简历优化建议。
        你必须只输出 JSON，不要输出 Markdown，不要输出额外解释。

        JSON 字段必须包含：
        summary, target_position, gap_summary, rewrite_suggestions, missing_keywords, action_items

        字段类型要求：
        - summary: 字符串，概括本次优化方向
        - target_position: 字符串，识别出的目标岗位
        - gap_summary: 字符串数组，概括简历和 JD 的主要差距
        - rewrite_suggestions: 对象数组，给出可直接落地的改写建议
        - missing_keywords: 字符串数组，JD 中重要但简历体现不足的关键词
        - action_items: 字符串数组，用户下一步应该补充或准备的事项

        rewrite_suggestions 每一项必须包含：
        section, issue, suggestion, before_text, after_text, keywords_added

        rewrite_suggestions 字段类型要求：
        - section: 字符串，例如 项目经历 / 技能栈 / 个人总结
        - issue: 字符串，说明当前简历表述的问题
        - suggestion: 字符串，说明修改思路
        - before_text: 字符串，引用或概括原简历中的待优化表述
        - after_text: 字符串，给出可以直接放进简历的优化后表述
        - keywords_added: 字符串数组，说明本次改写补进了哪些 JD 关键词

        原则：
        - 不要编造用户没有做过的经历。
        - 可以把已有经历改写得更贴合 JD。
        - 如果缺少经历，请放到 missing_keywords 或 action_items，不要伪造成已完成经验。
        - 如果某个字段无法识别，请返回空数组或“未识别”，不要省略字段。
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


def parse_resume_optimize_response(raw_answer: str) -> ResumeOptimizeResponse:
    json_text = extract_json_object(raw_answer)
    try:
        data = json.loads(json_text)
    except JSONDecodeError as error:
        raise BadRequestError(
            "模型返回的简历优化结果不是合法 JSON",
            details={"raw_answer": raw_answer[:500]},
        ) from error
    try:
        return ResumeOptimizeResponse(**data)
    except ValidationError as error:
        raise BadRequestError(
            "模型返回的简历优化字段不符合要求",
            details={"errors": error.errors()},
        ) from error


def optimize_resume(request: ResumeOptimizeRequest) -> ResumeOptimizeResponse:
    if not request.resume_text.strip():
        raise BadRequestError("简历内容不能为空")

    if not request.job_description.strip():
        raise BadRequestError("岗位 JD 不能为空")

    messages = build_resume_optimize_messages(request)
    raw_answer = chat_completion(messages)

    return parse_resume_optimize_response(raw_answer)


def build_interview_prep_messages(request: InterviewPrepRequest) -> list[dict]:
    system_prompt = dedent(
        """
        你是面试准备教练。
        请根据用户提供的简历文本和岗位 JD，生成一份结构化面试准备包。
        你必须只输出 JSON，不要输出 Markdown，不要输出额外解释。

        JSON 字段必须包含：
        target_position, self_introduction, project_talking_points, technical_questions, behavioral_questions, questions_to_ask, preparation_checklist

        字段类型要求：
        - target_position: 字符串，识别出的目标岗位
        - self_introduction: 字符串，适合 60 到 90 秒口述的中文自我介绍
        - project_talking_points: 对象数组，围绕简历项目生成项目讲解
        - technical_questions: 对象数组，围绕 JD 和项目生成技术追问
        - behavioral_questions: 对象数组，生成行为面试问题和回答要点
        - questions_to_ask: 字符串数组，候选人可以反问面试官的问题
        - preparation_checklist: 字符串数组，面试前需要复习或准备的清单

        project_talking_points 每一项必须包含：
        project_name, pitch, technical_depth, likely_followups

        project_talking_points 字段类型要求：
        - project_name: 字符串，项目名称
        - pitch: 字符串，项目讲解话术，突出背景、职责、技术方案和结果
        - technical_depth: 字符串数组，候选人需要能讲清楚的技术深度点
        - likely_followups: 字符串数组，面试官可能继续追问的问题

        technical_questions 和 behavioral_questions 每一项必须包含：
        question, answer_points

        问答字段类型要求：
        - question: 字符串，面试问题
        - answer_points: 字符串数组，回答这个问题时应该覆盖的要点

        原则：
        - 不要编造用户没有做过的项目经历。
        - 技术问题要紧扣简历项目和岗位 JD。
        - 回答要点要能帮助用户组织口述，不要写成过长文章。
        - 如果某个字段无法识别，请返回空数组或“未识别”，不要省略字段。
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


def parse_interview_prep_response(raw_answer: str) -> InterviewPrepResponse:
    json_text = extract_json_object(raw_answer)
    try:
        data = json.loads(json_text)
    except JSONDecodeError as error:
        raise BadRequestError(
            "模型返回的面试准备包不是合法 JSON",
            details={"raw_answer": raw_answer[:500]},
        ) from error
    try:
        return InterviewPrepResponse(**data)
    except ValidationError as error:
        raise BadRequestError(
            "模型返回的面试准备包字段不符合要求",
            details={"errors": error.errors()},
        ) from error


def prepare_interview(request: InterviewPrepRequest) -> InterviewPrepResponse:
    if not request.resume_text.strip():
        raise BadRequestError("简历内容不能为空")

    if not request.job_description.strip():
        raise BadRequestError("岗位 JD 不能为空")

    messages = build_interview_prep_messages(request)
    raw_answer = chat_completion(messages)

    return parse_interview_prep_response(raw_answer)


def build_star_interview_answer_messages(request: StarInterviewAnswerRequest) -> list[dict]:
    system_prompt = dedent(
        """
        你是面试 STAR 回答教练。
        请根据用户提供的简历、岗位 JD 和面试问题，生成一份适合中文口述的 STAR 回答。
        你必须只输出 JSON，不要输出 Markdown，不要输出额外解释。

        JSON 字段必须包含：
        target_position, question, situation, task, action, result, answer, highlights, follow_up_questions

        字段类型要求：
        - target_position: 字符串，识别出的目标岗位
        - question: 字符串，原始面试问题或经过轻微规范化后的问题
        - situation: 字符串，S，项目或经历背景
        - task: 字符串，T，候选人当时承担的目标或职责
        - action: 字符串数组，A，候选人采取的关键行动，建议 3 到 5 条
        - result: 字符串，R，最终结果、收益或复盘结论
        - answer: 字符串，把 S/T/A/R 串成 60 到 120 秒可口述中文答案
        - highlights: 字符串数组，这个回答想突出给面试官的能力点
        - follow_up_questions: 字符串数组，面试官可能继续追问的问题

        原则：
        - 不要编造用户没有做过的项目经历。
        - 如果结果没有明确量化数据，可以用工程结果、能力沉淀或复盘结论表达，不要虚构数字。
        - 回答要具体，优先使用简历中的项目、技术栈和岗位 JD 关键词。
        - 如果某个字段无法识别，请返回“未识别”或空数组，不要省略字段。
        """
    ).strip()

    user_prompt = dedent(
        f"""
        简历内容：
        {request.resume_text.strip()}

        岗位 JD：
        {request.job_description.strip()}

        面试问题：
        {request.question.strip()}
        """
    ).strip()

    return [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": user_prompt},
    ]


def parse_star_interview_answer_response(raw_answer: str) -> StarInterviewAnswerResponse:
    json_text = extract_json_object(raw_answer)
    try:
        data = json.loads(json_text)
    except JSONDecodeError as error:
        raise BadRequestError(
            "模型返回的 STAR 面试答案不是合法 JSON",
            details={"raw_answer": raw_answer[:500]},
        ) from error
    try:
        return StarInterviewAnswerResponse(**data)
    except ValidationError as error:
        raise BadRequestError(
            "模型返回的 STAR 面试答案字段不符合要求",
            details={"errors": error.errors()},
        ) from error


def generate_star_interview_answer(request: StarInterviewAnswerRequest) -> StarInterviewAnswerResponse:
    if not request.resume_text.strip():
        raise BadRequestError("简历内容不能为空")

    if not request.job_description.strip():
        raise BadRequestError("岗位 JD 不能为空")

    if not request.question.strip():
        raise BadRequestError("面试问题不能为空")

    messages = build_star_interview_answer_messages(request)
    raw_answer = chat_completion(messages)

    return parse_star_interview_answer_response(raw_answer)
