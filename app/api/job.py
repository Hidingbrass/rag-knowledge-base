from fastapi import APIRouter

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
from app.services.job_service import (
    analyze_job_match,
    generate_star_interview_answer,
    optimize_resume,
    parse_jd,
    parse_resume,
    prepare_interview,
)

router = APIRouter(prefix="/job", tags=["job"])


@router.post("/analyze", response_model=JobAnalyzeResponse)
def analyze_job(request: JobAnalyzeRequest) -> JobAnalyzeResponse:
    return analyze_job_match(request)


@router.post("/resume/parse", response_model=ResumeParseResponse)
def parse_resume_endpoint(request: ResumeParseRequest) -> ResumeParseResponse:
    return parse_resume(request)


@router.post("/jd/parse", response_model=JdParseResponse)
def parse_jd_endpoint(request: JdParseRequest) -> JdParseResponse:
    return parse_jd(request)


@router.post("/resume/optimize", response_model=ResumeOptimizeResponse)
def optimize_resume_endpoint(request: ResumeOptimizeRequest) -> ResumeOptimizeResponse:
    return optimize_resume(request)


@router.post("/interview/prepare", response_model=InterviewPrepResponse)
def prepare_interview_endpoint(request: InterviewPrepRequest) -> InterviewPrepResponse:
    return prepare_interview(request)


@router.post("/interview/star-answer", response_model=StarInterviewAnswerResponse)
def generate_star_interview_answer_endpoint(request: StarInterviewAnswerRequest) -> StarInterviewAnswerResponse:
    return generate_star_interview_answer(request)
