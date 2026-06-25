"""统一异常定义和 FastAPI 异常处理器。

这个文件的目标是让项目形成统一的错误出口。

分层规则：
- service 层遇到业务错误时，抛 AppError 的子类。
- api 层尽量只调用 service，不重复写 try/except。
- main.py 注册异常处理器，把 AppError 转成统一 HTTP JSON 响应。

这样做的好处：
- 错误响应格式更稳定。
- API 文件更薄，更像 Controller。
- 日志记录集中，不需要每个接口重复写 logger.exception。
"""

from typing import Any

from fastapi import Request
from fastapi.responses import JSONResponse

from app.core.logging import get_logger


logger = get_logger(__name__)


class AppError(Exception):
    """项目业务异常基类。

    参数：
    - message：给前端或调用方看的错误说明。
    - status_code：HTTP 状态码。
    - error_code：稳定的错误编码，便于前端或日志系统识别错误类型。
    - details：可选的额外信息，例如重复文档的 document_id。

    注意：
    不是所有异常都应该变成 AppError。只有“业务上可预期”的错误才适合。
    例如 PDF 没有文字、参数不合法、重复上传文档。
    """

    def __init__(
            self,
            message: str,
            status_code: int = 400,
            error_code: str = "APP_ERROR",
            details: dict[str, Any] | None = None,
    ):
        super().__init__(message)
        self.message = message
        self.status_code = status_code
        self.error_code = error_code
        self.details = details or {}

    def to_response(self) -> dict:
        """把业务异常转换成统一响应体。"""
        response = {
            "error_code": self.error_code,
            "message": self.message,
        }

        if self.details:
            response["details"] = self.details

        return response


class BadRequestError(AppError):
    """请求参数或上传内容不合法，对应 HTTP 400。"""

    def __init__(self, message: str, details: dict[str, Any] | None = None):
        super().__init__(
            message=message,
            status_code=400,
            error_code="BAD_REQUEST",
            details=details,
        )


class DuplicateDocumentError(AppError):
    """重复文档异常，对应 HTTP 409。

    参数：
    - existing_document：Qdrant payload 中已存在的文档信息。

    这个异常以前放在 document_service.py 中。
    现在移动到 core/exceptions.py，是为了让“业务异常类型”集中管理。
    """

    def __init__(self, existing_document: dict):
        self.existing_document = existing_document
        super().__init__(
            message="该文档已经上传",
            status_code=409,
            error_code="DUPLICATE_DOCUMENT",
            details={
                "document_id": existing_document.get("document_id"),
                "filename": existing_document.get("filename"),
            },
        )


async def app_error_handler(request: Request, error: AppError) -> JSONResponse:
    """把 AppError 转成 HTTP JSON 响应。

    参数：
    - request：FastAPI 当前请求对象，用来记录 method 和 url。
    - error：service 或 api 抛出的业务异常。

    返回：
    - JSONResponse：统一错误响应。
    """
    logger.warning(
        "业务异常: method=%s path=%s status=%s error_code=%s message=%s",
        request.method,
        request.url.path,
        error.status_code,
        error.error_code,
        error.message,
    )

    return JSONResponse(
        status_code=error.status_code,
        content=error.to_response(),
    )


async def unhandled_exception_handler(request: Request, error: Exception) -> JSONResponse:
    """兜底处理未预期异常，对应 HTTP 500。

    这里会用 logger.exception 记录完整堆栈，方便排查问题。
    """
    logger.exception(
        "未处理异常: method=%s path=%s error=%s",
        request.method,
        request.url.path,
        error,
    )

    return JSONResponse(
        status_code=500,
        content={
            "error_code": "INTERNAL_SERVER_ERROR",
            "message": str(error),
        },
    )


def register_exception_handlers(app) -> None:
    """给 FastAPI app 注册统一异常处理器。

    参数：
    - app：FastAPI 应用实例。

    使用位置：
    - app/main.py 创建 app 后调用。
    """
    app.add_exception_handler(AppError, app_error_handler)
    app.add_exception_handler(Exception, unhandled_exception_handler)
