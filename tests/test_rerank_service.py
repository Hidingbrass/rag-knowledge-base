"""rerank_service 的单元测试。

这里不调用真实 qwen3-rerank API。
测试通过 monkeypatch 替换 requests.post，模拟 DashScope 返回结果。

重点验证：
- 请求使用 settings 中的 URL、模型名和超时时间。
- Rerank 返回的 index 能正确映射回原始 source。
- 返回的新 source 会新增 rerank_score。
- 原始 sources 不被污染。
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
