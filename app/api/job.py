from fastapi import APIRouter, File, Form, UploadFile

from app.schemas.job import (
    InterviewPrepRequest,
    InterviewPrepResponse,
    JdParseRequest,
    JdParseResponse,
    JobAnalyzeRequest,
    JobAnalyzeResponse,
    JobAttachmentTextResponse,
    JobDeliveryPackageRequest,
    JobDeliveryPackageResponse,
    ResumeOptimizeRequest,
    ResumeOptimizeResponse,
    ResumeParseRequest,
    ResumeParseResponse,
    StarInterviewAnswerRequest,
    StarInterviewAnswerResponse,
)
from app.services.job_attachment_service import extract_job_text_from_attachment
from app.services.job_service import (
    analyze_job_match,
    generate_job_delivery_package,
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


@router.post("/jd/extract-text", response_model=JobAttachmentTextResponse)
async def extract_jd_text_endpoint(file: UploadFile = File(...)) -> JobAttachmentTextResponse:
    content = await file.read()
    return extract_job_text_from_attachment(
        file.filename or "unknown",
        file.content_type or "",
        content,
    )


@router.post("/analyze-from-file", response_model=JobAnalyzeResponse)
async def analyze_job_from_file_endpoint(
    resume_text: str = Form(...),
    file: UploadFile = File(...),
) -> JobAnalyzeResponse:
    content = await file.read()
    extracted = extract_job_text_from_attachment(
        file.filename or "unknown",
        file.content_type or "",
        content,
    )
    return analyze_job_match(JobAnalyzeRequest(
        resume_text=resume_text,
        job_description=extracted.text,
    ))


@router.post("/resume/optimize", response_model=ResumeOptimizeResponse)
def optimize_resume_endpoint(request: ResumeOptimizeRequest) -> ResumeOptimizeResponse:
    return optimize_resume(request)


@router.post("/interview/prepare", response_model=InterviewPrepResponse)
def prepare_interview_endpoint(request: InterviewPrepRequest) -> InterviewPrepResponse:
    return prepare_interview(request)


@router.post("/interview/star-answer", response_model=StarInterviewAnswerResponse)
def generate_star_interview_answer_endpoint(request: StarInterviewAnswerRequest) -> StarInterviewAnswerResponse:
    return generate_star_interview_answer(request)


@router.post("/delivery-package", response_model=JobDeliveryPackageResponse)
def generate_job_delivery_package_endpoint(request: JobDeliveryPackageRequest) -> JobDeliveryPackageResponse:
    return generate_job_delivery_package(request)
