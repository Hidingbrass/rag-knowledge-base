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
