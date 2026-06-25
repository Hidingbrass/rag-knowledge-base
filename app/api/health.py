"""健康检查路由。

这里放服务自身和 Qdrant 的健康检查接口。
"""

from fastapi import APIRouter

from app.services.qdrant_service import qdrant_client


router = APIRouter()


@router.get("/health")
def health_check():
    """检查 FastAPI 应用是否能正常响应。"""
    return {"status": "ok"}


@router.get("/qdrant/health")
def qdrant_health():
    """检查 Qdrant 是否可访问，并返回当前 collection 列表。"""
    collections = qdrant_client.get_collections()
    return {
        "status": "ok",
        "collections": [item.name for item in collections.collections],
    }
