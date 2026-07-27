from pydantic import BaseModel, Field

from app.core.config import settings


class ModelUsageSummary(BaseModel):
    models: list[str] = Field(default_factory=list)
    upstream_call_count: int = 0
    retry_count: int = 0
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    estimated_cost_yuan: float = 0.0


class ModelResponse(BaseModel):
    model_usage: ModelUsageSummary | None = None


class JobAnalyzeRequest(BaseModel):
    resume_text: str = Field(max_length=settings.max_job_text_chars)
    job_description: str = Field(max_length=settings.max_job_text_chars)


class JobAnalyzeResponse(ModelResponse):
    match_score: int = Field(ge=0, le=100)
    matched_skills: list[str]
    missing_skills: list[str]
    strengths: list[str]
    risks: list[str]
    suggestions: list[str]
    interview_questions: list[str]


class ResumeParseRequest(BaseModel):
    resume_text: str = Field(max_length=settings.max_job_text_chars)


class ResumeProject(BaseModel):
    name: str
    role: str
    tech_stack: list[str]
    description: str
    highlights: list[str]


class ResumeParseResponse(ModelResponse):
    target_roles: list[str]
    skills: list[str]
    projects: list[ResumeProject]
    work_experiences: list[str]
    education: list[str]
    certifications: list[str]
    strengths: list[str]
    keywords: list[str]


class JdParseRequest(BaseModel):
    job_description: str = Field(max_length=settings.max_job_text_chars)


class JdParseResponse(ModelResponse):
    job_title: str
    seniority: str
    required_skills: list[str]
    preferred_skills: list[str]
    responsibilities: list[str]
    requirements: list[str]
    keywords: list[str]
    risks: list[str]


class JobAttachmentTextResponse(BaseModel):
    filename: str
    source_type: str
    text: str
    warnings: list[str] = []


class ResumeOptimizeRequest(BaseModel):
    resume_text: str = Field(max_length=settings.max_job_text_chars)
    job_description: str = Field(default="", max_length=settings.max_job_text_chars)


class ResumeRewriteSuggestion(BaseModel):
    section: str
    issue: str
    suggestion: str
    before_text: str
    after_text: str
    keywords_added: list[str]


class ResumeOptimizeResponse(ModelResponse):
    summary: str
    target_position: str
    gap_summary: list[str]
    rewrite_suggestions: list[ResumeRewriteSuggestion]
    missing_keywords: list[str]
    action_items: list[str]


class InterviewPrepRequest(BaseModel):
    resume_text: str = Field(max_length=settings.max_job_text_chars)
    job_description: str = Field(max_length=settings.max_job_text_chars)


class InterviewQuestionAnswer(BaseModel):
    question: str
    answer_points: list[str]


class ProjectTalkingPoint(BaseModel):
    project_name: str
    pitch: str
    technical_depth: list[str]
    likely_followups: list[str]


class InterviewPrepResponse(ModelResponse):
    target_position: str
    self_introduction: str
    project_talking_points: list[ProjectTalkingPoint]
    technical_questions: list[InterviewQuestionAnswer]
    behavioral_questions: list[InterviewQuestionAnswer]
    questions_to_ask: list[str]
    preparation_checklist: list[str]


class StarInterviewAnswerRequest(BaseModel):
    resume_text: str = Field(max_length=settings.max_job_text_chars)
    job_description: str = Field(max_length=settings.max_job_text_chars)
    question: str = Field(max_length=settings.max_question_chars)


class StarInterviewAnswerResponse(ModelResponse):
    target_position: str
    question: str
    situation: str
    task: str
    action: list[str]
    result: str
    answer: str
    highlights: list[str]
    follow_up_questions: list[str]


class JobDeliveryPackageRequest(BaseModel):
    resume_text: str = Field(max_length=settings.max_job_text_chars)
    job_description: str = Field(max_length=settings.max_job_text_chars)


class JobDeliveryPackageResponse(ModelResponse):
    target_position: str
    self_introduction: str
    project_pitch: str
    architecture_talking_points: list[str]
    risk_response: list[str]
    closing_statement: str
    rehearsal_checklist: list[str]
