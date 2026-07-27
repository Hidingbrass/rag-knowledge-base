"""RAG 问答路由。

这里保留原有接口路径：
- /rag/chat
- /rag/chat/rerank

业务流程已经放到 rag_service。
"""

import json

from fastapi import APIRouter
from fastapi.responses import StreamingResponse

from app.schemas.rag import RagChatRequest, RerankRagChatRequest
from app.services.rag_service import (
    rag_chat_response,
    rag_chat_with_rerank_response,
    rag_chat_with_rerank_stream_events,
)
from app.services.model_runtime import (
    collect_model_usage,
    current_model_usage,
    summarize_model_usage,
)


router = APIRouter()


@router.post("/rag/chat")
def rag_chat(request: RagChatRequest):
    """无 Rerank 的 RAG 问答接口。"""
    with collect_model_usage():
        response = rag_chat_response(request)
        response["model_usage"] = current_model_usage()
        return response


@router.post("/rag/chat/rerank")
def rag_chat_with_rerank(request: RerankRagChatRequest):
    """带 Rerank 的 RAG 问答接口。"""
    with collect_model_usage():
        response = rag_chat_with_rerank_response(request)
        response["model_usage"] = current_model_usage()
        return response


@router.post("/rag/chat/rerank/stream")
def rag_chat_with_rerank_stream(request: RerankRagChatRequest):
    """返回 NDJSON 事件流，供 Spring Boot 代理到 AI 伴学前端。"""

    return StreamingResponse(
        stream_event_lines(request),
        media_type="application/x-ndjson",
        headers={
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
        },
    )


def stream_event_lines(request: RerankRagChatRequest):
    """把业务事件编码为 NDJSON，并安全收集跨线程迭代的模型用量。"""
    records = []
    events = iter(rag_chat_with_rerank_stream_events(request))

    while True:
        try:
            # Starlette 可能让相邻 next() 落在不同 Context。每次只在本次
            # next() 内绑定和重置，显式 records 负责跨迭代保留累计结果。
            with collect_model_usage(records):
                event = next(events)
        except StopIteration:
            break

        if event.get("type") in {"done", "error"}:
            event["model_usage"] = summarize_model_usage(records)
        yield json.dumps(event, ensure_ascii=False) + "\n"
