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
        return "answer [1]"

    monkeypatch.setattr(rag_service, "search_chunks", fake_search_chunks)
    monkeypatch.setattr(rag_service, "chat_completion", fake_chat_completion)

    response = rag_service.rag_chat_response(
        RagChatRequest(
            question="question",
            top_k=2,
            min_score=0.55,
        )
    )

    assert response["answer"] == "answer [1]"
    assert response["answer_validation"]["valid"] is True
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
        return "fallback answer [1]"

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

    assert response["answer"] == "fallback answer [1]"
    assert response["retrieval_mode"] == "vector_fallback"
    assert response["rerank_error"] == "rerank failed"
    assert len(response["sources"]) == 1
    assert response["sources"][0]["text"] == "fallback source"


def test_rerank_rag_uses_hybrid_candidates(monkeypatch):
    captured = {}

    def fake_hybrid_search_chunks(
            question,
            candidate_k,
            sparse_limit,
            document_id=None,
    ):
        captured["hybrid"] = {
            "question": question,
            "candidate_k": candidate_k,
            "sparse_limit": sparse_limit,
            "document_id": document_id,
        }
        return [
            {
                "text": "hybrid source",
                "fusion_score": 0.7,
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
        return "hybrid answer [1]"

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
            sparse_limit=5,
            document_id="doc-1",
        )
    )

    assert captured["hybrid"] == {
        "question": "question",
        "candidate_k": 6,
        "sparse_limit": 5,
        "document_id": "doc-1",
    }
    assert captured["rerank_sources"][0]["fusion_score"] == 0.7
    assert response["answer"] == "hybrid answer [1]"
    assert response["retrieval_mode"] == "rerank"
    assert response["candidate_retrieval_mode"] == "hybrid"


def test_rerank_rag_hybrid_fallback_runs_fresh_dense_search(monkeypatch):
    def fake_hybrid_search_chunks(
            question,
            candidate_k,
            sparse_limit,
            document_id=None,
    ):
        return [
            {
                "text": "hybrid RRF source",
                "fusion_score": 0.7,
                "filename": "demo.pdf",
                "page_number": 1,
                "chunk_index": 0,
            },
        ]

    def fake_search_chunks(question, top_k, document_id=None):
        return [
            {
                "text": "vector fallback source",
                "vector_score": 0.7,
                "filename": "demo.pdf",
                "page_number": 2,
                "chunk_index": 1,
            }
        ]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        raise RuntimeError("rerank failed")

    def fake_chat_completion(messages):
        assert "hybrid RRF source" not in messages[1]["content"]
        assert "vector fallback source" in messages[1]["content"]
        return "fallback answer [1]"

    monkeypatch.setattr(rag_service, "hybrid_search_chunks", fake_hybrid_search_chunks)
    monkeypatch.setattr(rag_service, "search_chunks", fake_search_chunks)
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

    assert response["answer"] == "fallback answer [1]"
    assert response["retrieval_mode"] == "vector_fallback"
    assert len(response["sources"]) == 1
    assert response["sources"][0]["text"] == "vector fallback source"


def test_rerank_rag_stream_emits_progress_sources_and_text_deltas(monkeypatch):
    def fake_search_chunks(question, top_k, document_id=None):
        return [{
            "text": "RAG 会先检索资料。",
            "vector_score": 0.82,
            "filename": "rag.md",
            "page_number": 1,
            "chunk_index": 0,
        }]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        return [{**sources[0], "rerank_score": 0.93}]

    def fake_chat_completion_stream(messages):
        assert "RAG 会先检索资料。" in messages[1]["content"]
        yield "先检索"
        yield "，再生成 [1]。"

    monkeypatch.setattr(rag_service, "search_chunks", fake_search_chunks)
    monkeypatch.setattr(rag_service, "rerank_chunks", fake_rerank_chunks)
    monkeypatch.setattr(
        rag_service,
        "chat_completion_stream",
        fake_chat_completion_stream,
    )

    events = list(rag_service.rag_chat_with_rerank_stream_events(
        RerankRagChatRequest(
            question="RAG 如何工作？",
            candidate_k=3,
            rerank_top_k=1,
            rerank_min_score=0.75,
            document_id="doc-1",
        )
    ))

    assert [event["type"] for event in events] == [
        "status",
        "status",
        "sources",
        "status",
        "delta",
        "delta",
        "verification",
        "done",
    ]
    assert events[0]["stage"] == "retrieving"
    assert events[1]["stage"] == "reranking"
    assert events[2]["sources"][0]["filename"] == "rag.md"
    assert events[3]["stage"] == "generating"
    assert "".join(
        event["content"] for event in events if event["type"] == "delta"
    ) == "先检索，再生成 [1]。"
    assert events[-1]["retrieval_mode"] == "rerank"


def test_rerank_rag_stream_rejection_still_finishes_cleanly(monkeypatch):
    monkeypatch.setattr(rag_service, "search_chunks", lambda *args, **kwargs: [])
    monkeypatch.setattr(rag_service, "rerank_chunks", lambda *args, **kwargs: [])

    events = list(rag_service.rag_chat_with_rerank_stream_events(
        RerankRagChatRequest(
            question="资料外的问题",
            candidate_k=3,
            rerank_top_k=1,
        )
    ))

    assert any(
        event == {"type": "delta", "content": rag_service.NO_RELEVANT_ANSWER}
        for event in events
    )
    assert events[-1]["type"] == "done"
