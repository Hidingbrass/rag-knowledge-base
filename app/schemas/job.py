from pydantic import BaseModel


class JobAnalyzeRequest(BaseModel):
    resume_text: str
    job_description: str


class JobAnalyzeResponse(BaseModel):
    match_score: int
    matched_skills: list[str]
    missing_skills: list[str]
    strengths: list[str]
    risks: list[str]
    suggestions: list[str]
    interview_questions: list[str]


class ResumeParseRequest(BaseModel):
    resume_text: str


class ResumeProject(BaseModel):
    name: str
    role: str
    tech_stack: list[str]
    description: str
    highlights: list[str]


class ResumeParseResponse(BaseModel):
    target_roles: list[str]
    skills: list[str]
    projects: list[ResumeProject]
    work_experiences: list[str]
    education: list[str]
    certifications: list[str]
    strengths: list[str]
    keywords: list[str]


class JdParseRequest(BaseModel):
    job_description: str


class JdParseResponse(BaseModel):
    job_title: str
    seniority: str
    required_skills: list[str]
    preferred_skills: list[str]
    responsibilities: list[str]
    requirements: list[str]
    keywords: list[str]
    risks: list[str]


class ResumeOptimizeRequest(BaseModel):
    resume_text: str
    job_description: str


class ResumeRewriteSuggestion(BaseModel):
    section: str
    issue: str
    suggestion: str
    before_text: str
    after_text: str
    keywords_added: list[str]


class ResumeOptimizeResponse(BaseModel):
    summary: str
    target_position: str
    gap_summary: list[str]
    rewrite_suggestions: list[ResumeRewriteSuggestion]
    missing_keywords: list[str]
    action_items: list[str]


class InterviewPrepRequest(BaseModel):
    resume_text: str
    job_description: str


class InterviewQuestionAnswer(BaseModel):
    question: str
    answer_points: list[str]


class ProjectTalkingPoint(BaseModel):
    project_name: str
    pitch: str
    technical_depth: list[str]
    likely_followups: list[str]


class InterviewPrepResponse(BaseModel):
    target_position: str
    self_introduction: str
    project_talking_points: list[ProjectTalkingPoint]
    technical_questions: list[InterviewQuestionAnswer]
    behavioral_questions: list[InterviewQuestionAnswer]
    questions_to_ask: list[str]
    preparation_checklist: list[str]


class StarInterviewAnswerRequest(BaseModel):
    resume_text: str
    job_description: str
    question: str


class StarInterviewAnswerResponse(BaseModel):
    target_position: str
    question: str
    situation: str
    task: str
    action: list[str]
    result: str
    answer: str
    highlights: list[str]
    follow_up_questions: list[str]
