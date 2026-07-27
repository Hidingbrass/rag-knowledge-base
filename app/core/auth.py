"""FastAPI 内部 API Key 认证。

这个模块提供一个 FastAPI 依赖项，用于校验请求头中的 X-API-Key。

设计原则：
- Docker Compose 强制配置 FASTAPI_API_KEY；本地单独启动时可按需配置。
- 如果本地开发未配置（空字符串），所有请求直接放行，避免影响纯 Python 调试。
- /health 端点永远不需要 API Key，方便健康检查和 smoke check。
- 当 API Key 不匹配时返回 401，不泄露内部信息。

使用方式：
    from app.core.auth import verify_api_key
    app = FastAPI(dependencies=[Depends(verify_api_key)])
"""

import secrets

from fastapi import HTTPException, Request, Security
from fastapi.security import APIKeyHeader

from app.core.config import settings


api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def verify_api_key(
    request: Request,
    provided_key: str | None = Security(api_key_header),
) -> None:
    """校验 X-API-Key 请求头。

    参数：
    - request：FastAPI 当前请求对象。

    返回：
    - None：校验通过时不返回任何内容。

    抛出：
    - HTTPException(status_code=401)：API Key 缺失或不匹配。
    """

    # /health 永远放行，方便 Docker healthcheck、smoke check、负载均衡探测。
    if request.url.path == "/health":
        return

    # 未配置 API Key 时直接放行，兼容本地开发和现有测试。
    expected_key = settings.fastapi_api_key
    if not expected_key:
        return

    if not provided_key or not secrets.compare_digest(provided_key, expected_key):
        raise HTTPException(
            status_code=401,
            detail={
                "error_code": "UNAUTHORIZED",
                "message": "API Key 无效或缺失",
            },
        )
