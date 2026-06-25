"""rag_service 的业务流程单元测试。

这里不调用真实 Qdrant、真实 Rerank、真实通义千问。
测试通过 monkeypatch 替换 rag_service 内部依赖，专注验证 RAG 编排逻辑。

覆盖重点：
- 普通 RAG 会按 min_score 过滤 source。
- 普通 RAG 没有可用 source 时会拒答。
- Rerank 抛异常时会进入 vector_fallback。
"""

from app.schemas.rag import RagChatRequest, RerankRagChatRequest
from app.services import rag_service


def test_rag_chat_response_filters_sources_by_min_score(monkeypatch):
    """普通 RAG 应只把 vector_score 达标的 source 放进 Prompt。"""

    def fake_search_chunks(question, top_k, document_id=None):
        return [
            {
                "text": "low score chunk",
                "vector_score": 0.3,
                "filename": "demo.pdf",
                "page_number": 1,
                "chunk_index": 0,
            },
            {
                "text": "high score chunk",
                "vector_score": 0.8,
                "filename": "demo.pdf",
                "page_number": 2,
                "chunk_index": 1,
            },
        ]

    def fake_chat_completion(messages):
        assert "high score chunk" in messages[1]["content"]
        assert "low score chunk" not in messages[1]["content"]
        return "answer"

    monkeypatch.setattr(rag_service, "search_chunks", fake_search_chunks)
    monkeypatch.setattr(rag_service, "chat_completion", fake_chat_completion)

    response = rag_service.rag_chat_response(
        RagChatRequest(
            question="question",
            top_k=2,
            min_score=0.55,
        )
    )

    assert response["answer"] == "answer"
    assert len(response["sources"]) == 1
    assert response["sources"][0]["text"] == "high score chunk"


def test_rag_chat_response_rejects_when_no_source_matches(monkeypatch):
    """普通 RAG 没有 source 达到 min_score 时，应直接拒答。"""

    def fake_search_chunks(question, top_k, document_id=None):
        return [
            {
                "text": "low score chunk",
                "vector_score": 0.3,
            }
        ]

    monkeypatch.setattr(rag_service, "search_chunks", fake_search_chunks)

    response = rag_service.rag_chat_response(
        RagChatRequest(
            question="question",
            top_k=1,
            min_score=0.55,
        )
    )

    assert response["answer"] == rag_service.NO_RELEVANT_ANSWER
    assert response["sources"] == []


def test_rerank_rag_falls_back_to_vector_sources_when_rerank_fails(monkeypatch):
    """Rerank API 失败时，应进入 vector_fallback 并继续用向量分数过滤。"""

    def fake_search_chunks(question, top_k, document_id=None):
        return [
            {
                "text": "fallback source",
                "vector_score": 0.7,
                "filename": "demo.pdf",
                "page_number": 1,
                "chunk_index": 0,
            },
            {
                "text": "low fallback source",
                "vector_score": 0.2,
                "filename": "demo.pdf",
                "page_number": 2,
                "chunk_index": 1,
            },
        ]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        raise RuntimeError("rerank failed")

    def fake_chat_completion(messages):
        assert "fallback source" in messages[1]["content"]
        assert "low fallback source" not in messages[1]["content"]
        return "fallback answer"

    monkeypatch.setattr(rag_service, "search_chunks", fake_search_chunks)
    monkeypatch.setattr(rag_service, "rerank_chunks", fake_rerank_chunks)
    monkeypatch.setattr(rag_service, "chat_completion", fake_chat_completion)

    response = rag_service.rag_chat_with_rerank_response(
        RerankRagChatRequest(
            question="question",
            candidate_k=2,
            rerank_top_k=1,
            fallback_min_score=0.55,
        )
    )

    assert response["answer"] == "fallback answer"
    assert response["retrieval_mode"] == "vector_fallback"
    assert response["rerank_error"] == "rerank failed"
    assert len(response["sources"]) == 1
    assert response["sources"][0]["text"] == "fallback source"


def test_rerank_rag_uses_hybrid_candidates(monkeypatch):
    captured = {}

    def fake_hybrid_search_chunks(
            question,
            candidate_k,
            keyword_limit,
            document_id=None,
    ):
        captured["hybrid"] = {
            "question": question,
            "candidate_k": candidate_k,
            "keyword_limit": keyword_limit,
            "document_id": document_id,
        }
        return [
            {
                "text": "hybrid source",
                "vector_score": 0.7,
                "keyword_score": 2,
                "filename": "demo.pdf",
                "page_number": 1,
                "chunk_index": 0,
            }
        ]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        captured["rerank_sources"] = sources
        return [
            {
                **sources[0],
                "rerank_score": 0.9,
            }
        ]

    def fake_chat_completion(messages):
        assert "hybrid source" in messages[1]["content"]
        return "hybrid answer"

    monkeypatch.setattr(rag_service, "hybrid_search_chunks", fake_hybrid_search_chunks)
    monkeypatch.setattr(rag_service, "rerank_chunks", fake_rerank_chunks)
    monkeypatch.setattr(rag_service, "chat_completion", fake_chat_completion)

    response = rag_service.rag_chat_with_rerank_response(
        RerankRagChatRequest(
            question="question",
            candidate_k=6,
            rerank_top_k=1,
            rerank_min_score=0.75,
            retrieval_mode="hybrid",
            keyword_limit=5,
            document_id="doc-1",
        )
    )

    assert captured["hybrid"] == {
        "question": "question",
        "candidate_k": 6,
        "keyword_limit": 5,
        "document_id": "doc-1",
    }
    assert captured["rerank_sources"][0]["keyword_score"] == 2
    assert response["answer"] == "hybrid answer"
    assert response["retrieval_mode"] == "rerank"


def test_rerank_rag_hybrid_fallback_skips_keyword_only_sources(monkeypatch):
    def fake_hybrid_search_chunks(
            question,
            candidate_k,
            keyword_limit,
            document_id=None,
    ):
        return [
            {
                "text": "keyword only source",
                "keyword_score": 2,
                "filename": "demo.pdf",
                "page_number": 1,
                "chunk_index": 0,
            },
            {
                "text": "vector fallback source",
                "vector_score": 0.7,
                "filename": "demo.pdf",
                "page_number": 2,
                "chunk_index": 1,
            },
        ]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        raise RuntimeError("rerank failed")

    def fake_chat_completion(messages):
        assert "keyword only source" not in messages[1]["content"]
        assert "vector fallback source" in messages[1]["content"]
        return "fallback answer"

    monkeypatch.setattr(rag_service, "hybrid_search_chunks", fake_hybrid_search_chunks)
    monkeypatch.setattr(rag_service, "rerank_chunks", fake_rerank_chunks)
    monkeypatch.setattr(rag_service, "chat_completion", fake_chat_completion)

    response = rag_service.rag_chat_with_rerank_response(
        RerankRagChatRequest(
            question="question",
            candidate_k=6,
            rerank_top_k=1,
            retrieval_mode="hybrid",
            fallback_min_score=0.55,
        )
    )

    assert response["answer"] == "fallback answer"
    assert response["retrieval_mode"] == "vector_fallback"
    assert len(response["sources"]) == 1
    assert response["sources"][0]["text"] == "vector fallback source"
