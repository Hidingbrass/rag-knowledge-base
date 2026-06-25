"""qdrant_service.search_chunks 的单元测试。

这里不连接真实 Qdrant，也不调用真实 Embedding。
测试通过 monkeypatch 替换：
- create_embedding：固定返回一个假向量。
- qdrant_client.query_points：固定返回几个假 points。

重点验证 search_chunks 的输出结构：
- RAG 链路使用 vector_score。
- 不返回 /search/demo 独立使用的 score 字段。
- document_id 会被传给 Qdrant 查询过滤条件。
"""

from types import SimpleNamespace

from app.services import qdrant_service


def test_search_chunks_returns_rag_source_with_vector_score(monkeypatch):
    """search_chunks 应返回 RAG Source，并使用 vector_score 字段。"""
    captured_query = {}

    def fake_create_embedding(question):
        captured_query["question"] = question
        return [0.1, 0.2, 0.3]

    class FakeQdrantClient:
        """只模拟 query_points 这一个测试需要的方法。"""

        def query_points(self, collection_name, query, limit, query_filter):
            captured_query["collection_name"] = collection_name
            captured_query["query"] = query
            captured_query["limit"] = limit
            captured_query["query_filter"] = query_filter

            return SimpleNamespace(
                points=[
                    SimpleNamespace(
                        score=0.87654,
                        payload={
                            "document_id": "doc-1",
                            "text": "chunk text",
                            "filename": "demo.pdf",
                            "chunk_index": 4,
                            "page_number": 2,
                        },
                    )
                ]
            )

    monkeypatch.setattr(qdrant_service, "create_embedding", fake_create_embedding)
    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    sources = qdrant_service.search_chunks(
        question="测试问题",
        top_k=3,
        document_id="doc-1",
    )

    assert captured_query["question"] == "测试问题"
    assert captured_query["query"] == [0.1, 0.2, 0.3]
    assert captured_query["limit"] == 3
    assert captured_query["query_filter"] is not None

    assert sources == [
        {
            "document_id": "doc-1",
            "vector_score": 0.8765,
            "text": "chunk text",
            "filename": "demo.pdf",
            "chunk_index": 4,
            "page_number": 2,
        }
    ]
    assert "score" not in sources[0]


def test_extract_keywords_keeps_chinese_and_english_terms():
    keywords = qdrant_service.extract_keywords("Redis 在项目中可以用于哪些场景？")

    assert "Redis" in keywords
    assert "在项目中可以用于哪些场景" in keywords


def test_keyword_search_chunks_returns_sorted_keyword_sources(monkeypatch):
    captured_scroll = {}

    class FakeQdrantClient:
        def scroll(
                self,
                collection_name,
                scroll_filter,
                limit,
                with_payload,
                with_vectors,
        ):
            captured_scroll["collection_name"] = collection_name
            captured_scroll["scroll_filter"] = scroll_filter
            captured_scroll["limit"] = limit
            captured_scroll["with_payload"] = with_payload
            captured_scroll["with_vectors"] = with_vectors

            return (
                [
                    SimpleNamespace(
                        payload={
                            "document_id": "doc-1",
                            "text": "Redis cache data",
                            "filename": "demo.pdf",
                            "chunk_index": 1,
                            "page_number": 2,
                        }
                    ),
                    SimpleNamespace(
                        payload={
                            "document_id": "doc-2",
                            "text": "Redis cache Redis",
                            "filename": "demo.pdf",
                            "chunk_index": 2,
                            "page_number": 3,
                        }
                    ),
                    SimpleNamespace(
                        payload={
                            "document_id": "doc-3",
                            "text": "Qdrant vector database",
                            "filename": "demo.pdf",
                            "chunk_index": 3,
                            "page_number": 4,
                        }
                    ),
                ],
                None,
            )

    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    sources = qdrant_service.keyword_search_chunks("Redis cache", limit=1)

    assert captured_scroll["collection_name"] == qdrant_service.COLLECTION_NAME
    assert captured_scroll["scroll_filter"] is None
    assert captured_scroll["limit"] == 1000
    assert captured_scroll["with_payload"] is True
    assert captured_scroll["with_vectors"] is False

    assert sources == [
        {
            "document_id": "doc-1",
            "text": "Redis cache data",
            "filename": "demo.pdf",
            "chunk_index": 1,
            "page_number": 2,
            "keyword_score": 2,
        }
    ]


def test_keyword_search_chunks_passes_document_filter_to_qdrant(monkeypatch):
    captured_scroll = {}

    class FakeQdrantClient:
        def scroll(
                self,
                collection_name,
                scroll_filter,
                limit,
                with_payload,
                with_vectors,
        ):
            captured_scroll["scroll_filter"] = scroll_filter

            return ([], None)

    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    sources = qdrant_service.keyword_search_chunks(
        question="Redis cache",
        limit=1,
        document_id="doc-1",
    )

    assert sources == []
    assert captured_scroll["scroll_filter"] is not None


def test_build_source_key_uses_document_id_and_chunk_index():
    source = {
        "document_id": "doc-1",
        "chunk_index": 3,
    }

    assert qdrant_service.build_source_key(source) == ("doc-1", 3)


def test_hybrid_search_chunks_merges_vector_and_keyword_sources(monkeypatch):
    captured_calls = {}

    vector_sources = [
        {
            "document_id": "doc-1",
            "text": "Redis cache",
            "filename": "demo.pdf",
            "chunk_index": 1,
            "page_number": 2,
            "vector_score": 0.8,
        },
        {
            "document_id": "doc-2",
            "text": "Qdrant vector database",
            "filename": "demo.pdf",
            "chunk_index": 2,
            "page_number": 3,
            "vector_score": 0.7,
        },
    ]
    keyword_sources = [
        {
            "document_id": "doc-1",
            "text": "Redis cache",
            "filename": "demo.pdf",
            "chunk_index": 1,
            "page_number": 2,
            "keyword_score": 2,
        },
        {
            "document_id": "doc-3",
            "text": "Redis session",
            "filename": "demo.pdf",
            "chunk_index": 3,
            "page_number": 4,
            "keyword_score": 1,
        },
    ]

    def fake_search_chunks(question, candidate_k, document_id=None):
        captured_calls["vector"] = {
            "question": question,
            "candidate_k": candidate_k,
            "document_id": document_id,
        }
        return vector_sources

    def fake_keyword_search_chunks(question, keyword_limit, document_id=None):
        captured_calls["keyword"] = {
            "question": question,
            "keyword_limit": keyword_limit,
            "document_id": document_id,
        }
        return keyword_sources

    monkeypatch.setattr(qdrant_service, "search_chunks", fake_search_chunks)
    monkeypatch.setattr(
        qdrant_service,
        "keyword_search_chunks",
        fake_keyword_search_chunks,
    )

    sources = qdrant_service.hybrid_search_chunks(
        question="Redis",
        candidate_k=6,
        keyword_limit=5,
        document_id="doc-1",
    )

    assert captured_calls["vector"] == {
        "question": "Redis",
        "candidate_k": 6,
        "document_id": "doc-1",
    }
    assert captured_calls["keyword"] == {
        "question": "Redis",
        "keyword_limit": 5,
        "document_id": "doc-1",
    }

    assert sources == [
        {
            "document_id": "doc-1",
            "text": "Redis cache",
            "filename": "demo.pdf",
            "chunk_index": 1,
            "page_number": 2,
            "vector_score": 0.8,
            "keyword_score": 2,
        },
        {
            "document_id": "doc-2",
            "text": "Qdrant vector database",
            "filename": "demo.pdf",
            "chunk_index": 2,
            "page_number": 3,
            "vector_score": 0.7,
        },
        {
            "document_id": "doc-3",
            "text": "Redis session",
            "filename": "demo.pdf",
            "chunk_index": 3,
            "page_number": 4,
            "keyword_score": 1,
        },
    ]
