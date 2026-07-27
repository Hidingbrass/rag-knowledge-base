"""岗位截图 / PDF 文字提取服务。

招聘网站经常有反爬策略，项目不应该强依赖网页爬虫。
这个服务允许用户上传岗位截图、图片或 PDF，先提取岗位文本，再交给求职 Agent 分析。
"""

from io import BytesIO

from pypdf import PdfReader

from app.core.config import settings
from app.core.exceptions import BadRequestError
from app.schemas.job import JobAttachmentTextResponse
from app.services.qwen_service import chat_completion_with_images


SUPPORTED_IMAGE_TYPES = {
    "image/png",
    "image/jpeg",
    "image/webp",
}
PDF_CONTENT_TYPE = "application/pdf"


def extract_job_text_from_attachment(
    filename: str,
    content_type: str,
    content: bytes,
) -> JobAttachmentTextResponse:
    """从岗位截图、图片或 PDF 中提取岗位文本。"""
    if not content:
        raise BadRequestError("上传文件不能为空")

    if len(content) > settings.max_upload_size_mb * 1024 * 1024:
        raise BadRequestError(f"文件大小不能超过 {settings.max_upload_size_mb}MB")

    normalized_content_type = (content_type or "").lower()
    normalized_filename = filename or "unknown"

    if normalized_content_type == PDF_CONTENT_TYPE or normalized_filename.lower().endswith(".pdf"):
        return extract_job_text_from_pdf(normalized_filename, content)

    if normalized_content_type in SUPPORTED_IMAGE_TYPES:
        text = extract_job_text_from_image(content, normalized_content_type)
        return JobAttachmentTextResponse(
            filename=normalized_filename,
            source_type="image_ocr",
            text=text,
            warnings=[],
        )

    raise BadRequestError("目前只支持 PDF、PNG、JPG、JPEG、WEBP 文件")


def extract_job_text_from_pdf(filename: str, content: bytes) -> JobAttachmentTextResponse:
    """优先读取文本 PDF；如果是扫描 PDF，再用视觉模型识别前几页。"""
    text = extract_text_from_pdf_content(content)
    if enough_text(text):
        return JobAttachmentTextResponse(
            filename=filename,
            source_type="pdf_text",
            text=text,
            warnings=[],
        )

    page_images = render_pdf_pages_to_png(content, settings.job_ocr_pdf_max_pages)
    if not page_images:
        raise BadRequestError("无法从 PDF 中提取岗位文字")

    text = extract_job_text_from_images(page_images)
    return JobAttachmentTextResponse(
        filename=filename,
        source_type="pdf_image_ocr",
        text=text,
        warnings=[f"该 PDF 可能是扫描件，已识别前 {len(page_images)} 页"],
    )


def extract_text_from_pdf_content(content: bytes) -> str:
    """从文本型 PDF 直接提取文字。"""
    try:
        reader = PdfReader(BytesIO(content))
        pages = [page.extract_text() or "" for page in reader.pages]
    except Exception as error:
        raise BadRequestError("PDF 文件解析失败") from error

    return normalize_extracted_text("\n".join(pages))


def render_pdf_pages_to_png(content: bytes, max_pages: int) -> list[tuple[bytes, str]]:
    """把扫描 PDF 前几页渲染成 PNG，供视觉模型识别。"""
    try:
        import fitz
    except ImportError as error:
        raise BadRequestError("当前环境缺少 PyMuPDF，无法识别扫描 PDF") from error

    try:
        document = fitz.open(stream=content, filetype="pdf")
        images = []
        for page_index in range(min(len(document), max_pages)):
            page = document.load_page(page_index)
            pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
            images.append((pixmap.tobytes("png"), "image/png"))
        document.close()
        return images
    except Exception as error:
        raise BadRequestError("扫描 PDF 图片渲染失败") from error


def extract_job_text_from_image(content: bytes, content_type: str) -> str:
    return extract_job_text_from_images([(content, content_type)])


def extract_job_text_from_images(images: list[tuple[bytes, str]]) -> str:
    prompt = (
        "请识别图片中的招聘岗位信息，尽量保留原文。"
        "只输出识别出的岗位文字，不要输出 Markdown，不要解释。"
        "如果图片中有公司、岗位名称、岗位职责、任职要求、技能关键词，请全部保留。"
    )
    raw_text = chat_completion_with_images(prompt, images)
    text = normalize_extracted_text(raw_text)
    if not enough_text(text):
        raise BadRequestError("未能从图片中识别出足够的岗位文字")
    return text


def enough_text(text: str) -> bool:
    return len((text or "").strip()) >= 20


def normalize_extracted_text(text: str) -> str:
    lines = [line.strip() for line in (text or "").splitlines()]
    return "\n".join(line for line in lines if line)
