"""Unit tests for dense, sparse and Hybrid RRF Qdrant retrieval."""

from types import SimpleNamespace

import pytest
from qdrant_client import QdrantClient
from qdrant_client.models import Fusion, FusionQuery, SparseVector

from app.services import qdrant_service


def _point(score=0.87654):
    return SimpleNamespace(
        score=score,
        payload={
            "document_id": "doc-1",
            "text": "Redis cache data",
            "filename": "demo.pdf",
            "chunk_index": 4,
            "page_number": 2,
        },
    )


def test_search_chunks_uses_named_dense_vector_and_document_filter(monkeypatch):
    captured = {}

    class FakeQdrantClient:
        def query_points(self, **kwargs):
            captured.update(kwargs)
            return SimpleNamespace(points=[_point()])

    monkeypatch.setattr(qdrant_service, "create_embedding", lambda question: [0.1, 0.2])
    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    sources = qdrant_service.search_chunks("测试问题", 3, "doc-1")

    assert captured["query"] == [0.1, 0.2]
    assert captured["using"] == qdrant_service.DENSE_VECTOR_NAME
    assert captured["limit"] == 3
    assert captured["query_filter"] is not None
    assert sources == [{
        "document_id": "doc-1",
        "vector_score": 0.8765,
        "text": "Redis cache data",
        "filename": "demo.pdf",
        "chunk_index": 4,
        "page_number": 2,
    }]


def test_sparse_search_uses_qdrant_sparse_index(monkeypatch):
    captured = {}

    class FakeQdrantClient:
        def query_points(self, **kwargs):
            captured.update(kwargs)
            return SimpleNamespace(points=[_point(1.23456)])

    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    sources = qdrant_service.sparse_search_chunks("Redis 缓存", 2, "doc-1")

    assert isinstance(captured["query"], SparseVector)
    assert captured["using"] == qdrant_service.SPARSE_VECTOR_NAME
    assert captured["query_filter"] is not None
    assert captured["limit"] == 2
    assert sources[0]["sparse_score"] == 1.2346
    assert "vector_score" not in sources[0]


def test_hybrid_search_uses_server_side_rrf(monkeypatch):
    captured = {}

    class FakeQdrantClient:
        def query_points(self, **kwargs):
            captured.update(kwargs)
            return SimpleNamespace(points=[_point(0.75)])

    monkeypatch.setattr(qdrant_service, "create_embedding", lambda question: [0.3, 0.4])
    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    sources = qdrant_service.hybrid_search_chunks(
        "Redis 缓存",
        candidate_k=6,
        sparse_limit=5,
        document_id="doc-1",
    )

    assert len(captured["prefetch"]) == 2
    assert captured["prefetch"][0].using == qdrant_service.DENSE_VECTOR_NAME
    assert captured["prefetch"][0].limit == 6
    assert captured["prefetch"][1].using == qdrant_service.SPARSE_VECTOR_NAME
    assert captured["prefetch"][1].limit == 5
    assert captured["prefetch"][0].filter is not None
    assert captured["prefetch"][1].filter is not None
    assert isinstance(captured["query"], FusionQuery)
    assert captured["query"].fusion == Fusion.RRF
    assert sources[0]["fusion_score"] == 0.75


def test_upsert_writes_dense_and_sparse_named_vectors(monkeypatch):
    captured = {}

    class FakeQdrantClient:
        def get_collections(self):
            return SimpleNamespace(collections=[])

        def create_collection(self, **kwargs):
            captured["create"] = kwargs

        def upsert(self, **kwargs):
            captured["upsert"] = kwargs

    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    qdrant_service.upsert_document_chunks(
        chunks=[{"text": "Redis 缓存", "page_number": 1}],
        vectors=[[0.1, 0.2]],
        filename="demo.pdf",
        file_hash="hash",
    )

    assert qdrant_service.DENSE_VECTOR_NAME in captured["create"]["vectors_config"]
    assert qdrant_service.SPARSE_VECTOR_NAME in captured["create"]["sparse_vectors_config"]
    vector = captured["upsert"]["points"][0].vector
    assert vector[qdrant_service.DENSE_VECTOR_NAME] == [0.1, 0.2]
    assert isinstance(vector[qdrant_service.SPARSE_VECTOR_NAME], SparseVector)
    assert vector[qdrant_service.SPARSE_VECTOR_NAME].indices


def test_old_unnamed_vector_collection_is_rejected(monkeypatch):
    class FakeQdrantClient:
        def get_collections(self):
            return SimpleNamespace(collections=[SimpleNamespace(name=qdrant_service.COLLECTION_NAME)])

        def get_collection(self, collection_name):
            params = SimpleNamespace(vectors=SimpleNamespace(size=1024), sparse_vectors={})
            return SimpleNamespace(config=SimpleNamespace(params=params))

    monkeypatch.setattr(qdrant_service, "qdrant_client", FakeQdrantClient())

    with pytest.raises(RuntimeError, match="旧版单向量结构"):
        qdrant_service.ensure_collection()


def test_in_memory_qdrant_executes_sparse_and_rrf_queries(monkeypatch):
    client = QdrantClient(":memory:")
    dense_vector = [1.0, *([0.0] * (qdrant_service.settings.embedding_dimensions - 1))]
    monkeypatch.setattr(qdrant_service, "qdrant_client", client)
    monkeypatch.setattr(qdrant_service, "create_embedding", lambda question: dense_vector)

    document_id = qdrant_service.upsert_document_chunks(
        chunks=[{"text": "Redis 可以实现接口限流", "page_number": 1}],
        vectors=[dense_vector],
        filename="demo.pdf",
        file_hash="hash",
    )

    sparse_sources = qdrant_service.sparse_search_chunks("Redis 限流", 3, document_id)
    hybrid_sources = qdrant_service.hybrid_search_chunks("Redis 限流", 3, 3, document_id)

    assert sparse_sources[0]["document_id"] == document_id
    assert sparse_sources[0]["sparse_score"] > 0
    assert hybrid_sources[0]["document_id"] == document_id
    assert hybrid_sources[0]["fusion_score"] > 0
