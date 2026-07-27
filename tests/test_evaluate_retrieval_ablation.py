import pytest

from app.evaluation import evaluate_retrieval_ablation as ablation


def test_retrieve_for_mode_routes_to_each_retrieval_branch(monkeypatch):
    calls = []

    monkeypatch.setattr(
        ablation,
        "search_chunks",
        lambda question, limit: calls.append(("vector", limit)) or [{"text": "v"}],
    )
    monkeypatch.setattr(
        ablation,
        "sparse_search_chunks",
        lambda question, limit: calls.append(("sparse", limit)) or [{"text": "s"}],
    )
    monkeypatch.setattr(
        ablation,
        "hybrid_search_chunks",
        lambda question, dense_limit, sparse_limit: calls.append(
            ("hybrid", dense_limit, sparse_limit)
        ) or [{"text": "h"}],
    )
    monkeypatch.setattr(
        ablation,
        "rerank_chunks",
        lambda question, sources, limit, diversity: calls.append(
            ("rerank", limit, diversity)
        ) or sources,
    )

    for mode in ablation.RETRIEVAL_MODES:
        ablation.retrieve_for_mode(mode, "question", 6, 5, 3)

    assert calls == [
        ("vector", 6),
        ("sparse", 5),
        ("hybrid", 6, 5),
        ("hybrid", 6, 5),
        ("rerank", 3, True),
    ]


def test_evaluate_retrieval_mode_reports_hit_and_latency(monkeypatch):
    monkeypatch.setattr(ablation, "TEST_CASES", [
        {
            "question": "answerable",
            "should_reject": False,
            "expected_page": 2,
        },
        {
            "question": "unknown",
            "should_reject": True,
            "expected_page": None,
        },
    ])
    monkeypatch.setattr(
        ablation,
        "retrieve_for_mode",
        lambda *args: [{"page_number": 2, "fusion_score": 0.7}],
    )

    evaluation = ablation.evaluate_retrieval_mode("hybrid")

    assert evaluation["metrics"]["hit_at_k"] == 1.0
    assert evaluation["metrics"]["normal_count"] == 1
    assert evaluation["metrics"]["p50_elapsed_ms"] >= 0
    assert evaluation["results"][1]["hit"] is None


def test_evaluate_retrieval_mode_reports_document_mrr_and_recall(monkeypatch):
    cases = [{
        "id": "multi-doc",
        "category": "multi_document",
        "question": "question",
        "should_reject": False,
        "gold_documents": ["security.pdf", "evaluation.pdf"],
    }]
    monkeypatch.setattr(
        ablation,
        "retrieve_for_mode",
        lambda *args: [
            {"filename": "other.pdf", "fusion_score": 0.8},
            {"filename": "security.pdf", "fusion_score": 0.7},
            {"filename": "evaluation.pdf", "fusion_score": 0.6},
        ],
    )

    evaluation = ablation.evaluate_retrieval_mode(
        "hybrid",
        cases=cases,
        dataset_name="technical_docs",
    )

    assert evaluation["config"]["dataset"] == "technical_docs"
    assert evaluation["config"]["result_k"] == 6
    assert evaluation["config"]["rerank_document_diversity"] is True
    assert evaluation["metrics"]["hit_at_k"] == 1.0
    assert evaluation["metrics"]["mrr_at_k"] == 0.5
    assert evaluation["metrics"]["mean_gold_document_recall"] == 1.0
    assert evaluation["metrics"]["full_gold_hit_at_k"] == 1.0
    assert evaluation["results"][0]["retrieved_documents"] == [
        "other.pdf",
        "security.pdf",
        "evaluation.pdf",
    ]


def test_evaluate_retrieval_mode_rejects_unknown_mode():
    with pytest.raises(ValueError):
        ablation.evaluate_retrieval_mode("unknown")


def test_rerank_ablation_reports_final_result_k(monkeypatch):
    monkeypatch.setattr(
        ablation,
        "retrieve_for_mode",
        lambda *args: [{"filename": "gold.pdf", "rerank_score": 0.9}],
    )
    cases = [{
        "question": "question",
        "should_reject": False,
        "gold_documents": ["gold.pdf"],
    }]

    evaluation = ablation.evaluate_retrieval_mode(
        "hybrid_rerank",
        rerank_top_k=3,
        cases=cases,
    )

    assert evaluation["config"]["result_k"] == 3
