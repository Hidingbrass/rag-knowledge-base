"""FastAPI 应用入口。

main.py 现在只负责两件事：
1. 创建 FastAPI app。
2. 注册各个 api router。

项目分层：
- api：HTTP 路由层，负责请求/响应和异常转换。
- schemas：Pydantic 数据结构。
- services：业务逻辑和外部服务调用。

这样 main.py 会保持很薄，后续继续加功能时不会重新变成“大杂烩”。
"""

from fastapi import FastAPI

from app.api.chat import router as chat_router
from app.api.documents import router as documents_router
from app.api.health import router as health_router
from app.api.rag import router as rag_router
from app.api.search import router as search_router
from app.core.exceptions import register_exception_handlers
from app.core.logging import get_logger, setup_logging


setup_logging()
logger = get_logger(__name__)

app = FastAPI(title="RAG Knowledge Base")
register_exception_handlers(app)

app.include_router(health_router)
app.include_router(chat_router)
app.include_router(documents_router)
app.include_router(search_router)
app.include_router(rag_router)

logger.info("FastAPI app 初始化完成，路由已注册")
