"""rerank_service 的单元测试。

这里不调用真实 qwen3-rerank API。
测试通过 monkeypatch 替换 requests.post，模拟 DashScope 返回结果。

重点验证：
- 请求使用 settings 中的 URL、模型名和超时时间。
- Rerank 返回的 index 能正确映射回原始 source。
- 返回的新 source 会新增 rerank_score。
- 原始 sources 不被污染。
- 文档多样性选择不会让单文档问题丢失补充 Chunk。
"""

from app.core.config import settings
from app.services import rerank_service


class FakeRerankResponse:
    """模拟 requests.post 返回的 response 对象。"""

    def raise_for_status(self):
        """模拟 HTTP 状态正常。"""

    def json(self):
        """模拟 qwen3-rerank 返回的排序结果。"""
        return {
            "results": [
                {
                    "index": 1,
                    "relevance_score": 0.91,
                },
                {
                    "index": 0,
                    "relevance_score": 0.42,
                },
            ]
        }


def test_rerank_chunks_maps_index_back_to_original_source(monkeypatch):
    """Rerank 应根据返回 index 找回原 source，并新增 rerank_score。"""
    captured_request = {}

    def fake_post(url, headers, json, timeout):
        captured_request["url"] = url
        captured_request["headers"] = headers
        captured_request["json"] = json
        captured_request["timeout"] = timeout
        return FakeRerankResponse()

    monkeypatch.setattr(rerank_service.requests, "post", fake_post)

    sources = [
        {
            "text": "first chunk",
            "vector_score": 0.7,
            "page_number": 1,
        },
        {
            "text": "second chunk",
            "vector_score": 0.8,
            "page_number": 2,
        },
    ]

    reranked_sources = rerank_service.rerank_chunks(
        question="test question",
        sources=sources,
        rerank_top_k=2,
    )

    assert captured_request["url"] == settings.rerank_api_url
    assert captured_request["json"]["model"] == settings.rerank_model
    assert captured_request["json"]["documents"] == ["first chunk", "second chunk"]
    assert captured_request["json"]["top_n"] == 2
    assert captured_request["timeout"] == settings.request_timeout_seconds
    assert captured_request["headers"]["Authorization"] == f"Bearer {settings.dashscope_api_key}"

    assert reranked_sources == [
        {
            "text": "second chunk",
            "vector_score": 0.8,
            "page_number": 2,
            "rerank_score": 0.91,
        },
        {
            "text": "first chunk",
            "vector_score": 0.7,
            "page_number": 1,
            "rerank_score": 0.42,
        },
    ]

    assert "rerank_score" not in sources[0]
    assert "rerank_score" not in sources[1]


def test_rerank_chunks_returns_empty_list_when_sources_empty(monkeypatch):
    """没有候选 source 时，Rerank 不应该发起外部请求。"""
    called = False

    def fake_post(*args, **kwargs):
        nonlocal called
        called = True
        return FakeRerankResponse()

    monkeypatch.setattr(rerank_service.requests, "post", fake_post)

    assert rerank_service.rerank_chunks("question", [], 3) == []
    assert called is False


def test_select_diverse_sources_prioritizes_distinct_documents():
    """第一轮应跳过同文档重复 Chunk，保留其他高分文档。"""
    ranked_sources = [
        {"text": "a-1", "document_id": "doc-a", "rerank_score": 0.99},
        {"text": "a-2", "document_id": "doc-a", "rerank_score": 0.98},
        {"text": "b-1", "document_id": "doc-b", "rerank_score": 0.90},
        {"text": "c-1", "document_id": "doc-c", "rerank_score": 0.80},
    ]

    selected = rerank_service.select_diverse_sources(ranked_sources, 3)

    assert [source["text"] for source in selected] == ["a-1", "b-1", "c-1"]


def test_select_diverse_sources_fuses_retrieval_and_rerank_document_ranks():
    """关键文档在召回阶段靠前时，不应被 Rerank 单阶段排序完全抹掉。"""
    ranked_sources = [
        {
            "text": "ops",
            "document_id": "doc-ops",
            "rerank_score": 0.53,
            "_retrieval_rank": 5,
        },
        {
            "text": "eval-high",
            "document_id": "doc-eval",
            "rerank_score": 0.49,
            "_retrieval_rank": 2,
        },
        {
            "text": "eval-retrieval-first",
            "document_id": "doc-eval",
            "rerank_score": 0.45,
            "_retrieval_rank": 0,
        },
        {
            "text": "arch",
            "document_id": "doc-arch",
            "rerank_score": 0.40,
            "_retrieval_rank": 3,
        },
        {
            "text": "security",
            "document_id": "doc-security",
            "rerank_score": 0.37,
            "_retrieval_rank": 1,
        },
    ]

    selected = rerank_service.select_diverse_sources(ranked_sources, 3)

    assert [source["text"] for source in selected] == [
        "eval-high",
        "ops",
        "security",
    ]
    assert all("_retrieval_rank" not in source for source in selected)


def test_select_diverse_sources_fills_remaining_slots_for_single_document():
    """只有一个文档时，第二轮仍应按 Rerank 顺序补满 Top K。"""
    ranked_sources = [
        {"text": "a-1", "filename": "a.pdf", "rerank_score": 0.99},
        {"text": "a-2", "filename": "a.pdf", "rerank_score": 0.90},
        {"text": "a-3", "filename": "a.pdf", "rerank_score": 0.80},
    ]

    selected = rerank_service.select_diverse_sources(ranked_sources, 3)

    assert [source["text"] for source in selected] == ["a-1", "a-2", "a-3"]


def test_rerank_chunks_requests_all_candidates_when_diversity_enabled(monkeypatch):
    """要从第四名补入新文档，Rerank API 必须返回完整候选排序。"""
    captured_payload = {}

    class FullRerankResponse:
        def raise_for_status(self):
            pass

        def json(self):
            return {
                "results": [
                    {"index": 0, "relevance_score": 0.99},
                    {"index": 1, "relevance_score": 0.98},
                    {"index": 2, "relevance_score": 0.90},
                    {"index": 3, "relevance_score": 0.80},
                ]
            }

    def fake_post(url, headers, json, timeout):
        captured_payload.update(json)
        return FullRerankResponse()

    monkeypatch.setattr(rerank_service.requests, "post", fake_post)
    sources = [
        {"text": "a-1", "document_id": "doc-a"},
        {"text": "a-2", "document_id": "doc-a"},
        {"text": "b-1", "document_id": "doc-b"},
        {"text": "c-1", "document_id": "doc-c"},
    ]

    selected = rerank_service.rerank_chunks(
        "question",
        sources,
        3,
        diversify_by_document=True,
    )

    assert captured_payload["top_n"] == 4
    assert [source["text"] for source in selected] == ["a-1", "b-1", "c-1"]


def test_rerank_chunks_can_disable_document_diversity(monkeypatch):
    """关闭开关时保持模型原始 Top K，并只请求所需结果数量。"""
    captured_payload = {}

    def fake_post(url, headers, json, timeout):
        captured_payload.update(json)
        return FakeRerankResponse()

    monkeypatch.setattr(rerank_service.requests, "post", fake_post)
    sources = [
        {"text": "first", "document_id": "doc-a"},
        {"text": "second", "document_id": "doc-a"},
    ]

    selected = rerank_service.rerank_chunks(
        "question",
        sources,
        1,
        diversify_by_document=False,
    )

    assert captured_payload["top_n"] == 1
    assert [source["text"] for source in selected] == ["second"]
