from app.evaluation import evaluate_generation_rerank as rerank_generation_module
from app.evaluation import evaluate_rerank_params as rerank_params_module


def test_compare_rerank_min_scores_marks_recommended_score(monkeypatch):
    metrics_by_score = {
        0.72: {
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 0.875,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
        0.75: {
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
        0.78: {
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
    }

    def fake_evaluate_generation_rerank(
            candidate_k,
            rerank_top_k,
            rerank_min_score,
    ):
        return {"metrics": metrics_by_score[rerank_min_score]}

    monkeypatch.setattr(
        rerank_generation_module,
        "evaluate_generation_rerank",
        fake_evaluate_generation_rerank,
    )

    results = rerank_generation_module.compare_rerank_min_scores(
        candidate_k=6,
        rerank_top_k=3,
        repeat_count=1,
    )

    recommended_scores = [
        result["rerank_min_score"]
        for result in results
        if result["is_recommended"]
    ]

    assert recommended_scores == [0.75]
    assert results[1]["chunk_support_pass_rate"] == 1.0


def test_evaluate_rerank_params_includes_quality_metrics(monkeypatch):
    def fake_evaluate_generation_rerank(
            candidate_k,
            rerank_top_k,
            rerank_min_score,
    ):
        metrics = {
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
            "fallback_rate": 0.0,
            "average_rerank_elapsed_seconds": candidate_k / 10,
            "slow_rerank_rate": 0.0,
        }

        return {"metrics": metrics}

    monkeypatch.setattr(
        rerank_params_module,
        "evaluate_generation_rerank",
        fake_evaluate_generation_rerank,
    )

    results = rerank_params_module.evaluate_rerank_params(repeat_count=1)

    assert results[0]["citation_valid_rate"] == 1.0
    assert results[0]["average_citation_support"] == 1.0
    assert results[0]["chunk_support_pass_rate"] == 1.0
    assert results[0]["is_recommended"] is True
