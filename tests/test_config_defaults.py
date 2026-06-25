"""配置默认值的回归测试。

配置管理完成后，接口请求模型的默认参数应该和 settings 保持一致。
这个文件专门防止以后有人在 schema 里重新写死默认值，导致评测脚本和接口参数不一致。
"""

from app.core.config import settings
from app.schemas.rag import RagChatRequest, RerankRagChatRequest
import pytest
from pydantic import ValidationError


def test_rag_request_defaults_come_from_settings():
    """普通 RAG 请求默认值应来自 app.core.config.settings。"""
    request = RagChatRequest(question="什么是 RAG？")

    assert request.top_k == settings.default_top_k
    assert request.min_score == settings.default_min_score
    assert request.document_id is None


def test_rerank_rag_request_defaults_come_from_settings():
    """Rerank RAG 请求默认值应来自 app.core.config.settings。"""
    request = RerankRagChatRequest(question="什么是 RAG？")

    assert request.candidate_k == settings.default_candidate_k
    assert request.rerank_top_k == settings.default_rerank_top_k
    assert request.rerank_min_score == settings.default_rerank_min_score
    assert request.fallback_min_score == settings.default_fallback_min_score
    assert request.document_id is None
    assert request.retrieval_mode == "vector"
    assert request.keyword_limit == 6


def test_rerank_rag_request_accepts_hybrid_retrieval_mode():
    request = RerankRagChatRequest(
        question="什么是 RAG？",
        retrieval_mode="hybrid",
        keyword_limit=8,
    )

    assert request.retrieval_mode == "hybrid"
    assert request.keyword_limit == 8


def test_rerank_rag_request_rejects_invalid_retrieval_mode():
    with pytest.raises(ValidationError):
        RerankRagChatRequest(
            question="什么是 RAG？",
            retrieval_mode="invalid",
        )


def test_rerank_rag_request_rejects_invalid_keyword_limit():
    with pytest.raises(ValidationError):
        RerankRagChatRequest(
            question="什么是 RAG？",
            keyword_limit=0,
        )
