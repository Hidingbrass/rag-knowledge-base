"""普通聊天接口的数据结构。

schemas 目录只放 Pydantic 模型，不写业务逻辑。
这个文件描述 /chat 接口需要接收什么数据。
"""

from typing import Literal

from pydantic import BaseModel, Field


class ChatMessage(BaseModel):
    """一条历史聊天消息。

    字段：
    - role：消息角色，目前只允许 user 或 assistant。
    - content：消息正文。
    """

    role: Literal["user", "assistant"]
    content: str


class ChatRequest(BaseModel):
    """普通聊天接口请求体。

    字段：
    - question：用户本轮问题。
    - history：历史对话列表，默认空列表。

    history 使用 default_factory=list，避免多个请求共享同一个默认列表。
    """

    question: str
    history: list[ChatMessage] = Field(default_factory=list)
