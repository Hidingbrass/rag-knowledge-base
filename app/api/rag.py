"""RAG 问答路由。

这里保留原有接口路径：
- /rag/chat
- /rag/chat/rerank

业务流程已经放到 rag_service。
"""

from fastapi import APIRouter

from app.schemas.rag import RagChatRequest, RerankRagChatRequest
from app.services.rag_service import rag_chat_response, rag_chat_with_rerank_response


router = APIRouter()


@router.post("/rag/chat")
def rag_chat(request: RagChatRequest):
    """无 Rerank 的 RAG 问答接口。"""
    return rag_chat_response(request)


@router.post("/rag/chat/rerank")
def rag_chat_with_rerank(request: RerankRagChatRequest):
    """带 Rerank 的 RAG 问答接口。"""
    return rag_chat_with_rerank_response(request)
