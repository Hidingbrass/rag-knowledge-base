from fastapi import APIRouter

from app.schemas.job import JobAnalyzeRequest, JobAnalyzeResponse
from app.services.job_service import analyze_job_match

router = APIRouter(prefix="/job", tags=["job"])


@router.post("/analyze", response_model=JobAnalyzeResponse)
def analyze_job(request: JobAnalyzeRequest) -> JobAnalyzeResponse:
    return analyze_job_match(request)
