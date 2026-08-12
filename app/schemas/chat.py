"""普通聊天接口的数据结构。

schemas 目录只放 Pydantic 模型，不写业务逻辑。
这个文件描述 /chat 接口需要接收什么数据。
"""

from typing import Literal

from pydantic import BaseModel, Field

from app.core.config import settings


class ChatMessage(BaseModel):
    """一条历史聊天消息。

    字段：
    - role：消息角色，目前只允许 user 或 assistant。
    - content：消息正文。
    """

    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=settings.max_question_chars)


class ChatRequest(BaseModel):
    """普通聊天接口请求体。

    字段：
    - question：用户本轮问题。
    - history：历史对话列表，默认空列表。

    history 使用 default_factory=list，避免多个请求共享同一个默认列表。
    """

    question: str = Field(min_length=1, max_length=settings.max_question_chars)
    history: list[ChatMessage] = Field(default_factory=list, max_length=20)
    mode: Literal["open_domain_chat", "tool_call", "clarification"] = "open_domain_chat"


class IntentClassificationRequest(BaseModel):
    """规则路由未命中后，供内部查询路由器判断本轮意图。"""

    question: str = Field(min_length=1, max_length=settings.max_question_chars)


class IntentClassifierOutput(BaseModel):
    """分类模型必须返回的最小 JSON 契约。"""

    intent: Literal[
        "KNOWLEDGE_QA",
        "TOOL_CALL",
        "CLARIFICATION",
        "OPEN_DOMAIN_CHAT",
    ]
    confidence: float = Field(ge=0.0, le=1.0)
    enterprise_knowledge: bool
    reason_code: Literal[
        "enterprise_knowledge",
        "tool_request",
        "needs_clarification",
        "open_domain",
    ]
    knowledge_scope: Literal["ENTERPRISE", "PUBLIC", "UNKNOWN"] = "UNKNOWN"
    operation: Literal["ANSWER", "READ_TOOL", "WRITE_TOOL", "CLARIFY"] = "ANSWER"
    freshness: Literal["REALTIME", "STATIC", "UNKNOWN"] = "UNKNOWN"
    tool_name: str | None = Field(default=None, max_length=64)
    missing_fields: list[str] = Field(default_factory=list, max_length=10)
    requires_confirmation: bool = False
