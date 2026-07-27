"""文档管理路由。

这里保留原有接口路径：
- /documents/preview
- /documents/index
- /documents/{document_id}
- /documents

HTTP 层只负责：
- 校验上传文件类型。
- 读取文件 bytes。
- 调用 document_service 或 qdrant_service。
- 业务异常交给全局异常处理器统一转换成 HTTP 响应。
"""

from fastapi import APIRouter, File, UploadFile

from app.core.config import settings
from app.core.exceptions import BadRequestError
from app.services.document_service import (
    index_document as index_document_service,
    preview_document as preview_document_service,
)
from app.services.qdrant_service import delete_document_points, list_indexed_documents
from app.services.model_runtime import collect_model_usage, current_model_usage


router = APIRouter()

# 读取时多读 1 字节，用来判断是否超限，同时避免把超大文件完整读入内存。
_MAX_READ_SIZE = settings.max_upload_size_mb * 1024 * 1024 + 1


SUPPORTED_DOCUMENT_EXTENSIONS = {".pdf", ".md", ".markdown", ".docx", ".txt"}


def ensure_supported_document(file: UploadFile):
    """校验上传文件是否属于允许入库的文档格式。

    参数：
    - file：FastAPI UploadFile。

    抛出：
    - BadRequestError：当文件扩展名不在支持列表中。
    """
    filename = (file.filename or "").lower()
    if not any(filename.endswith(extension) for extension in SUPPORTED_DOCUMENT_EXTENSIONS):
        raise BadRequestError("支持 PDF、Markdown、Word（DOCX）和 TXT 文件")


def ensure_file_size(content: bytes):
    """校验上传文件是否超过大小限制。

    参数：
    - content：文件字节内容。

    抛出：
    - BadRequestError：当文件超过最大上传大小时。
    """
    max_size = settings.max_upload_size_mb
    if len(content) > max_size * 1024 * 1024:
        raise BadRequestError(f"文件大小不能超过 {max_size}MB")


@router.post("/documents/preview")
async def preview_document(file: UploadFile = File(...)):
    """预览文档解析和切分结果，但不写入 Qdrant。"""
    ensure_supported_document(file)
    content = await file.read(_MAX_READ_SIZE)
    ensure_file_size(content)

    return preview_document_service(file.filename or "unknown", content)


@router.post("/documents/index")
async def index_document(file: UploadFile = File(...)):
    """上传受支持文档、解析切分、向量化并写入 Qdrant。"""
    ensure_supported_document(file)
    content = await file.read(_MAX_READ_SIZE)
    ensure_file_size(content)

    with collect_model_usage():
        response = index_document_service(file.filename or "unknown", content)
        response["model_usage"] = current_model_usage()
        return response


@router.delete("/documents/{document_id}")
def delete_document(document_id: str):
    """按 document_id 删除文档的全部向量片段。"""
    delete_document_points(document_id)

    return {
        "document_id": document_id,
        "deleted": True,
    }


@router.get("/documents")
def list_documents():
    """列出当前已入库文档及其 chunk 数量。"""
    documents = list_indexed_documents()

    return {
        "documents": documents,
        "total": len(documents),
    }
