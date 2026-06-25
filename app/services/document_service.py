"""文档处理服务。

这个文件负责 PDF 文档从“原始上传内容”到“可入库 chunks”的业务流程。

主要职责：
- 解析 PDF 文本。
- 按句子和页码切分文本。
- 计算文件 hash，用于重复检测。
- 调用 Embedding 和 Qdrant 服务完成文档入库。

为什么放在 service 层：
- API 层只应该关心 HTTP 请求和响应。
- PDF 解析、切分、hash、入库编排都属于业务逻辑。
"""

import hashlib
import re
from io import BytesIO

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


def preview_pdf_document(filename: str, content: bytes) -> dict:
    """预览 PDF 解析和切分结果，但不写入 Qdrant。

    参数：
    - filename：上传文件名。
    - content：上传文件内容 bytes。

    返回：
    - 文件名、页数、文本长度、chunk 数量和前 3 个预览 chunk。
    """
    logger.info("开始预览 PDF: filename=%s size=%s", filename, len(content))
    reader, text = read_pdf(content)
    chunks = split_text(text)
    logger.info(
        "PDF 预览完成: filename=%s page_count=%s chunk_count=%s",
        filename,
        len(reader.pages),
        len(chunks),
    )

    return {
        "filename": filename,
        "page_count": len(reader.pages),
        "text_length": len(text),
        "chunk_count": len(chunks),
        "preview_chunks": chunks[:3],
    }


def index_pdf_document(filename: str, content: bytes) -> dict:
    """解析 PDF、生成 Embedding，并写入 Qdrant。

    参数：
    - filename：上传文件名。
    - content：上传文件内容 bytes。

    返回：
    - 入库结果，包括 filename、chunk_count、collection、document_id、file_hash。

    流程：
    1. 计算 file_hash。
    2. 检查重复文档。
    3. 解析 PDF。
    4. 按页切分 chunk。
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

    reader, _ = read_pdf(content)
    chunks = split_pdf_pages(reader)
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
        "chunk_count": len(chunks),
        "collection": COLLECTION_NAME,
        "document_id": document_id,
        "file_hash": file_hash,
    }
