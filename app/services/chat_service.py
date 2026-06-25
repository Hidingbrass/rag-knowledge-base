"""普通聊天服务。

这个文件负责 /chat 接口的业务逻辑。

它不使用知识库检索，只把用户问题和历史消息组织成 messages，
然后调用 qwen_service.chat_completion 生成回答。
"""

from app.schemas.chat import ChatRequest
from app.services.qwen_service import chat_completion


def chat_response(request: ChatRequest) -> dict:
    """执行普通聊天。

    参数：
    - request：包含 question 和 history 的 ChatRequest。

    返回：
    - {"answer": "..."}，保持原 /chat 接口响应结构不变。

    关键变量：
    - messages：发送给 qwen-plus 的 OpenAI-compatible 消息列表。
    """
    messages = [
        {
            "role": "system",
            "content": "你是一名耐心、准确的 AI 学习助手。",
        }
    ]

    for message in request.history:
        messages.append(message.model_dump())

    messages.append(
        {
            "role": "user",
            "content": request.question,
        }
    )

    answer = chat_completion(messages)

    return {
        "answer": answer,
    }
