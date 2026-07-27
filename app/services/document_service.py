"""文档处理服务。

这个文件负责 PDF、Markdown、DOCX 和 TXT 从原始内容到可入库 chunks 的业务流程。

主要职责：
- 按文件格式提取文本。
- 按句子和逻辑页码切分文本。
- 计算文件 hash，用于重复检测。
- 调用 Embedding 和 Qdrant 服务完成文档入库。

为什么放在 service 层：
- API 层只应该关心 HTTP 请求和响应。
- 文档解析、切分、hash、入库编排都属于业务逻辑。
"""

import hashlib
import re
from io import BytesIO
from pathlib import PurePath
from xml.etree import ElementTree
from zipfile import BadZipFile, ZipFile

from pypdf import PdfReader

from app.core.exceptions import BadRequestError, DuplicateDocumentError
from app.core.logging import get_logger
from app.services.qdrant_service import (
    COLLECTION_NAME,
    find_document_by_hash,
    upsert_document_chunks,
)
from app.services.qwen_service import create_embeddings


logger = get_logger(__name__)

SUPPORTED_DOCUMENT_EXTENSIONS = {".pdf", ".md", ".markdown", ".docx", ".txt"}
WORDPROCESSINGML_NAMESPACE = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"


def split_text(text: str, chunk_size: int = 350, overlap_sentences: int = 1) -> list[str]:
    """把长文本切分成多个 chunk。

    参数：
    - text：从 PDF 提取出来的原始文本。
    - chunk_size：单个 chunk 的最大字符数。
    - overlap_sentences：相邻 chunk 之间保留多少个重叠句子。

    返回：
    - chunk 字符串列表。

    关键变量：
    - paragraphs：按空行切分后的段落。
    - sentences：按句号、问号等标点切分后的句子。
    - current_sentences：当前正在累积的 chunk 句子。
    """
    paragraphs = [
        paragraph.strip()
        for paragraph in re.split(r"\n+", text)
        if paragraph.strip()
    ]
    sentences = []

    for paragraph in paragraphs:
        parts = re.split(r"(?<=[。！？；.!?;])", paragraph)
        sentences.extend(part.strip() for part in parts if part.strip())

    chunks = []
    current_sentences = []
    current_length = 0

    for sentence in sentences:
        if current_length + len(sentence) <= chunk_size:
            current_sentences.append(sentence)
            current_length += len(sentence)
            continue

        if current_sentences:
            chunks.append("".join(current_sentences))

        overlap = current_sentences[-overlap_sentences:] if overlap_sentences > 0 else []
        current_sentences = [*overlap, sentence]
        current_length = sum(len(item) for item in current_sentences)

    if current_sentences:
        chunks.append("".join(current_sentences))

    return chunks


def read_pdf(content: bytes) -> tuple[PdfReader, str]:
    """读取 PDF 二进制内容并提取全文。

    参数：
    - content：上传文件读取出的 bytes。

    返回：
    - reader：PdfReader 实例，后续还会用于逐页切分。
    - text：整份 PDF 提取出的文本。

    抛出：
    - BadRequestError：PDF 解析失败或没有提取到文字。
    """
    try:
        reader = PdfReader(BytesIO(content))
        pages = []

        for page in reader.pages:
            pages.append(page.extract_text() or "")

        text = "\n".join(pages).strip()
    except Exception as error:
        raise BadRequestError(f"PDF 解析失败: {error}") from error

    if not text:
        raise BadRequestError("未提取到文字，该 PDF 可能是扫描图片")

    return reader, text


def split_pdf_pages(reader: PdfReader) -> list[dict]:
    """按页解析 PDF，并为每个 chunk 保留页码。

    参数：
    - reader：pypdf.PdfReader 实例。

    返回：
    - chunk 字典列表，每个元素包含 text 和 page_number。

    为什么保留 page_number：
    - RAG 回答需要给出引用来源。
    - 评测脚本也会用页码判断检索是否命中预期资料。
    """
    result = []

    for page_number, page in enumerate(reader.pages, start=1):
        page_text = page.extract_text() or ""

        if not page_text.strip():
            continue

        page_chunks = split_text(page_text)

        for chunk_text in page_chunks:
            result.append(
                {
                    "text": chunk_text,
                    "page_number": page_number,
                }
            )

    return result


def read_text_document(content: bytes) -> str:
    """读取 Markdown/TXT，优先使用 UTF-8，并兼容常见中文 GB18030 编码。"""
    for encoding in ("utf-8-sig", "gb18030"):
        try:
            text = content.decode(encoding).strip()
            break
        except UnicodeDecodeError:
            continue
    else:
        raise BadRequestError("文本文件编码无法识别，请使用 UTF-8 编码")

    if not text:
        raise BadRequestError("文档内容不能为空")
    return text


def read_docx(content: bytes) -> str:
    """从 DOCX 的 WordprocessingML 中提取段落和表格文字。"""
    try:
        with ZipFile(BytesIO(content)) as archive:
            document_xml = archive.read("word/document.xml")
        root = ElementTree.fromstring(document_xml)
    except (BadZipFile, KeyError, ElementTree.ParseError) as error:
        raise BadRequestError("Word 文档解析失败，请确认文件是有效的 DOCX") from error

    paragraph_tag = f"{{{WORDPROCESSINGML_NAMESPACE}}}p"
    text_tag = f"{{{WORDPROCESSINGML_NAMESPACE}}}t"
    paragraphs = []
    for paragraph in root.iter(paragraph_tag):
        paragraph_text = "".join(
            node.text or ""
            for node in paragraph.iter(text_tag)
        ).strip()
        if paragraph_text:
            paragraphs.append(paragraph_text)

    text = "\n".join(paragraphs).strip()
    if not text:
        raise BadRequestError("未从 Word 文档中提取到文字")
    return text


def text_to_logical_page_chunks(text: str) -> list[dict]:
    """给没有固定页码的文本类文档分配逻辑页 1，保持引用结构兼容。"""
    return [
        {"text": chunk_text, "page_number": 1}
        for chunk_text in split_text(text)
    ]


def extract_document(filename: str, content: bytes) -> dict:
    """按扩展名解析文档并返回统一的文本与 chunk 结构。"""
    extension = PurePath(filename).suffix.lower()
    if extension not in SUPPORTED_DOCUMENT_EXTENSIONS:
        raise BadRequestError("支持 PDF、Markdown、Word（DOCX）和 TXT 文件")
    if not content:
        raise BadRequestError("文档内容不能为空")

    if extension == ".pdf":
        reader, text = read_pdf(content)
        chunks = split_pdf_pages(reader)
        page_count = len(reader.pages)
        document_type = "PDF"
    elif extension == ".docx":
        text = read_docx(content)
        chunks = text_to_logical_page_chunks(text)
        page_count = 1
        document_type = "DOCX"
    else:
        text = read_text_document(content)
        chunks = text_to_logical_page_chunks(text)
        page_count = 1
        document_type = "MARKDOWN" if extension in {".md", ".markdown"} else "TXT"

    if not chunks:
        raise BadRequestError("文档没有可用于学习的文字内容")

    return {
        "document_type": document_type,
        "page_count": page_count,
        "text": text,
        "chunks": chunks,
    }


def preview_document(filename: str, content: bytes) -> dict:
    """预览文档解析和切分结果，但不写入 Qdrant。

    参数：
    - filename：上传文件名。
    - content：上传文件内容 bytes。

    返回：
    - 文件名、页数、文本长度、chunk 数量和前 3 个预览 chunk。
    """
    logger.info("开始预览文档: filename=%s size=%s", filename, len(content))
    extracted = extract_document(filename, content)
    chunks = extracted["chunks"]
    logger.info(
        "文档预览完成: filename=%s document_type=%s page_count=%s chunk_count=%s",
        filename,
        extracted["document_type"],
        extracted["page_count"],
        len(chunks),
    )

    return {
        "filename": filename,
        "document_type": extracted["document_type"],
        "page_count": extracted["page_count"],
        "text_length": len(extracted["text"]),
        "chunk_count": len(chunks),
        "preview_chunks": [chunk["text"] for chunk in chunks[:3]],
    }


def index_document(filename: str, content: bytes) -> dict:
    """解析受支持文档、生成 Embedding，并写入 Qdrant。

    参数：
    - filename：上传文件名。
    - content：上传文件内容 bytes。

    返回：
    - 入库结果，包括 filename、chunk_count、collection、document_id、file_hash。

    流程：
    1. 计算 file_hash。
    2. 检查重复文档。
    3. 按文件格式解析正文。
    4. 按物理页或逻辑页切分 chunk。
    5. 批量生成 Embedding。
    6. 调用 qdrant_service 写入向量。
    """
    logger.info("开始文档入库: filename=%s size=%s", filename, len(content))
    file_hash = hashlib.sha256(content).hexdigest()
    existing_document = find_document_by_hash(file_hash)

    if existing_document:
        logger.warning(
            "检测到重复文档: filename=%s existing_document_id=%s",
            filename,
            existing_document.get("document_id"),
        )
        raise DuplicateDocumentError(existing_document)

    extracted = extract_document(filename, content)
    chunks = extracted["chunks"]
    chunk_texts = [chunk["text"] for chunk in chunks]
    vectors = create_embeddings(chunk_texts)
    document_id = upsert_document_chunks(
        chunks=chunks,
        vectors=vectors,
        filename=filename,
        file_hash=file_hash,
    )
    logger.info(
        "文档入库完成: filename=%s document_id=%s chunk_count=%s collection=%s",
        filename,
        document_id,
        len(chunks),
        COLLECTION_NAME,
    )

    return {
        "filename": filename,
        "document_type": extracted["document_type"],
        "chunk_count": len(chunks),
        "collection": COLLECTION_NAME,
        "document_id": document_id,
        "file_hash": file_hash,
    }


def preview_pdf_document(filename: str, content: bytes) -> dict:
    """兼容旧调用名称；实际走统一文档预览流程。"""
    return preview_document(filename, content)


def index_pdf_document(filename: str, content: bytes) -> dict:
    """兼容旧调用名称；实际走统一文档入库流程。"""
    return index_document(filename, content)
