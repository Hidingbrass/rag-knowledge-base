from io import BytesIO
from zipfile import ZipFile

import pytest

from app.core.exceptions import BadRequestError
from app.services.document_service import extract_document, preview_document


def build_docx(*paragraphs: str) -> bytes:
    body = "".join(
        f"<w:p><w:r><w:t>{paragraph}</w:t></w:r></w:p>"
        for paragraph in paragraphs
    )
    document_xml = (
        '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
        '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">'
        f"<w:body>{body}</w:body></w:document>"
    )
    buffer = BytesIO()
    with ZipFile(buffer, "w") as archive:
        archive.writestr("word/document.xml", document_xml)
    return buffer.getvalue()


def test_extract_markdown_preserves_learning_content():
    result = extract_document(
        "java-notes.md",
        "# Java 并发\n\n可见性由 happens-before 规则保证。".encode("utf-8"),
    )

    assert result["document_type"] == "MARKDOWN"
    assert result["page_count"] == 1
    assert result["chunks"][0]["page_number"] == 1
    assert "happens-before" in result["chunks"][0]["text"]


def test_extract_txt_supports_common_chinese_encoding():
    result = extract_document("复习计划.txt", "每天复习一个知识点。".encode("gb18030"))

    assert result["document_type"] == "TXT"
    assert result["text"] == "每天复习一个知识点。"


def test_extract_docx_reads_wordprocessingml_paragraphs():
    result = extract_document(
        "面试笔记.docx",
        build_docx("Spring Bean 生命周期。", "RAG 需要检索与重排。"),
    )

    assert result["document_type"] == "DOCX"
    assert "Spring Bean 生命周期" in result["text"]
    assert "RAG 需要检索与重排" in result["text"]
    assert result["chunks"][0]["page_number"] == 1


def test_preview_document_reports_type_and_plain_chunk_text():
    result = preview_document("notes.markdown", b"# Redis\n\nCache aside pattern.")

    assert result["document_type"] == "MARKDOWN"
    assert result["chunk_count"] == 1
    assert result["preview_chunks"] == ["# RedisCache aside pattern."]


def test_extract_docx_rejects_invalid_archive():
    with pytest.raises(BadRequestError, match="有效的 DOCX"):
        extract_document("broken.docx", b"not-a-docx")


def test_extract_document_rejects_legacy_word_and_unknown_formats():
    with pytest.raises(BadRequestError, match="支持 PDF"):
        extract_document("legacy.doc", b"legacy")

    with pytest.raises(BadRequestError, match="支持 PDF"):
        extract_document("table.csv", b"a,b")
