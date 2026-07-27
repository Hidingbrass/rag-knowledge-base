"""FastAPI API Key 认证。

这个模块提供一个 FastAPI 依赖项，用于校验请求头中的 X-API-Key。

设计原则：
- 只有在配置了 FASTAPI_API_KEY 时才启用校验。
- 如果未配置（空字符串），所有请求直接放行，方便本地开发和测试。
- /health 端点永远不需要 API Key，方便健康检查和 smoke check。
- 当 API Key 不匹配时返回 401，不泄露内部信息。

使用方式：
    from app.core.auth import verify_api_key
    app = FastAPI(dependencies=[Depends(verify_api_key)])
"""

from fastapi import Request, HTTPException

from app.core.config import settings


async def verify_api_key(request: Request) -> None:
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

    # 从请求头读取调用方传入的 API Key。
    provided_key = request.headers.get("X-API-Key", "")

    if provided_key != expected_key:
        raise HTTPException(
            status_code=401,
            detail={
                "error_code": "UNAUTHORIZED",
                "message": "API Key 无效或缺失",
            },
        )
