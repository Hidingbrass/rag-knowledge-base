import pytest

from app.core.exceptions import BadRequestError
from app.services import job_attachment_service


def test_extract_job_text_from_image_calls_vision_model(monkeypatch):
    captured = {}

    def fake_chat_completion_with_images(prompt, images):
        captured["prompt"] = prompt
        captured["images"] = images
        return """
        Java 后端开发工程师
        岗位要求：Spring Boot、MySQL、Redis、RAG 项目经验。
        """

    monkeypatch.setattr(
        job_attachment_service,
        "chat_completion_with_images",
        fake_chat_completion_with_images,
    )

    result = job_attachment_service.extract_job_text_from_attachment(
        "jd.png",
        "image/png",
        b"fake-image-content",
    )

    assert result.filename == "jd.png"
    assert result.source_type == "image_ocr"
    assert "Spring Boot" in result.text
    assert captured["images"] == [(b"fake-image-content", "image/png")]
    assert "招聘岗位信息" in captured["prompt"]


def test_extract_job_text_from_pdf_uses_direct_pdf_text(monkeypatch):
    monkeypatch.setattr(
        job_attachment_service,
        "extract_text_from_pdf_content",
        lambda content: "岗位要求：Java、Spring Boot、MySQL、Redis、RAG 项目经验。",
    )

    result = job_attachment_service.extract_job_text_from_attachment(
        "jd.pdf",
        "application/pdf",
        b"fake-pdf-content",
    )

    assert result.source_type == "pdf_text"
    assert "Redis" in result.text


def test_extract_job_text_from_scanned_pdf_uses_rendered_images(monkeypatch):
    monkeypatch.setattr(
        job_attachment_service,
        "extract_text_from_pdf_content",
        lambda content: "",
    )
    monkeypatch.setattr(
        job_attachment_service,
        "render_pdf_pages_to_png",
        lambda content, max_pages: [(b"page-1", "image/png")],
    )
    monkeypatch.setattr(
        job_attachment_service,
        "extract_job_text_from_images",
        lambda images: "岗位要求：Python、FastAPI、RAG、向量数据库。",
    )

    result = job_attachment_service.extract_job_text_from_attachment(
        "jd.pdf",
        "application/pdf",
        b"fake-pdf-content",
    )

    assert result.source_type == "pdf_image_ocr"
    assert result.text == "岗位要求：Python、FastAPI、RAG、向量数据库。"
    assert result.warnings == ["该 PDF 可能是扫描件，已识别前 1 页"]


def test_extract_job_text_rejects_unsupported_file():
    with pytest.raises(BadRequestError):
        job_attachment_service.extract_job_text_from_attachment(
            "jd.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            b"content",
        )


def test_extract_job_text_rejects_empty_file():
    with pytest.raises(BadRequestError):
        job_attachment_service.extract_job_text_from_attachment(
            "jd.png",
            "image/png",
            b"",
        )
