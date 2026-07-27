"""普通聊天路由。

/chat 不使用知识库检索，只调用普通大模型聊天服务。
"""

from fastapi import APIRouter

from app.schemas.chat import ChatRequest
from app.services.chat_service import chat_response
from app.services.model_runtime import collect_model_usage, current_model_usage


router = APIRouter()


@router.post("/chat")
def chat(request: ChatRequest):
    """普通聊天接口。"""
    with collect_model_usage():
        response = chat_response(request)
        response["model_usage"] = current_model_usage()
        return response
