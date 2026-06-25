
"""RAG 问答接口的数据结构。

这个文件只定义请求参数，不负责检索、Rerank 或模型调用。
把请求模型放在 schemas 中，可以让 main.py 和 service 层都复用同一套参数定义。
"""

from typing import Literal

from pydantic import BaseModel, Field

from app.core.config import settings


class RagChatRequest(BaseModel):
    """无 Rerank 的 RAG 问答请求体。

    字段：
    - question：用户问题。
    - top_k：向量检索返回的候选片段数量。
    - min_score：Qdrant 向量相似度阈值，低于该值的片段不会进入 Prompt。
    - document_id：可选，只在指定文档内检索。
    """

    question: str
    top_k: int = Field(default=settings.default_top_k, ge=1, le=10)
    min_score: float = Field(default=settings.default_min_score, ge=0.0, le=1.0)
    document_id: str | None = None


class RerankRagChatRequest(BaseModel):
    """带 Rerank 的 RAG 问答请求体。

    字段：
    - question：用户问题。
    - candidate_k：向量检索阶段召回的候选片段数量。
    - rerank_top_k：Rerank 后保留进入 Prompt 的片段数量。
    - rerank_min_score：Rerank 最高分低于该阈值时拒答。
    - document_id：可选，只在指定文档内检索。
    - fallback_min_score：Rerank API 失败时，回退到向量检索链路使用的阈值。
    """

    question: str
    candidate_k: int = Field(default=settings.default_candidate_k, ge=1, le=20)
    rerank_top_k: int = Field(default=settings.default_rerank_top_k, ge=1, le=10)
    rerank_min_score: float = Field(default=settings.default_rerank_min_score, ge=0.0, le=1.0)
    document_id: str | None = None
    fallback_min_score: float = Field(default=settings.default_fallback_min_score, ge=0.0, le=1.0)
    retrieval_mode: Literal["vector", "hybrid"] = "vector"
    keyword_limit: int = Field(default=6, ge=1, le=20)
