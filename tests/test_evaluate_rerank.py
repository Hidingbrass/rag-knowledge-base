import pytest

from app.evaluation import evaluate_rerank as rerank_module


def test_evaluate_rerank_uses_vector_candidates(monkeypatch):
    captured = {}
    test_cases = [
        {
            "question": "question",
            "should_reject": False,
            "expected_page": 1,
        }
    ]

    def fake_search_chunks(question, candidate_k):
        captured["vector"] = {
            "question": question,
            "candidate_k": candidate_k,
        }
        return [
            {
                "text": "vector source",
                "vector_score": 0.8,
                "page_number": 1,
            }
        ]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        return [
            {
                **sources[0],
                "rerank_score": 0.9,
            }
        ]

    monkeypatch.setattr(rerank_module, "TEST_CASES", test_cases)
    monkeypatch.setattr(rerank_module, "search_chunks", fake_search_chunks)
    monkeypatch.setattr(rerank_module, "rerank_chunks", fake_rerank_chunks)

    evaluation = rerank_module.evaluate_rerank(
        candidate_k=6,
        rerank_top_k=3,
        retrieval_mode="vector",
    )

    assert captured["vector"] == {
        "question": "question",
        "candidate_k": 6,
    }
    assert evaluation["config"]["retrieval_mode"] == "vector"
    assert evaluation["metrics"]["candidate_hit_rate"] == 1.0


def test_evaluate_rerank_uses_hybrid_candidates(monkeypatch):
    captured = {}
    test_cases = [
        {
            "question": "question",
            "should_reject": False,
            "expected_page": 1,
        }
    ]

    def fake_hybrid_search_chunks(question, candidate_k, keyword_limit):
        captured["hybrid"] = {
            "question": question,
            "candidate_k": candidate_k,
            "keyword_limit": keyword_limit,
        }
        return [
            {
                "text": "hybrid source",
                "keyword_score": 2,
                "page_number": 1,
            }
        ]

    def fake_rerank_chunks(question, sources, rerank_top_k):
        return [
            {
                **sources[0],
                "rerank_score": 0.9,
            }
        ]

    monkeypatch.setattr(rerank_module, "TEST_CASES", test_cases)
    monkeypatch.setattr(rerank_module, "hybrid_search_chunks", fake_hybrid_search_chunks)
    monkeypatch.setattr(rerank_module, "rerank_chunks", fake_rerank_chunks)

    evaluation = rerank_module.evaluate_rerank(
        candidate_k=6,
        rerank_top_k=3,
        retrieval_mode="hybrid",
        keyword_limit=5,
    )

    assert captured["hybrid"] == {
        "question": "question",
        "candidate_k": 6,
        "keyword_limit": 5,
    }
    assert evaluation["config"]["retrieval_mode"] == "hybrid"
    assert evaluation["config"]["keyword_limit"] == 5
    assert evaluation["results"][0]["candidate_hit"] is True
    assert evaluation["results"][0]["vector_scores"] == [None]
    assert evaluation["results"][0]["keyword_scores"] == [2]


def test_evaluate_rerank_rejects_invalid_retrieval_mode():
    with pytest.raises(ValueError):
        rerank_module.evaluate_rerank(retrieval_mode="invalid")


def test_compare_rerank_retrieval_modes_marks_recommended_mode(monkeypatch):
    captured_modes = []

    def fake_evaluate_rerank(
            candidate_k,
            rerank_top_k,
            rerank_min_score,
            retrieval_mode,
            keyword_limit,
    ):
        captured_modes.append(retrieval_mode)
        if retrieval_mode == "vector":
            metrics = {
                "candidate_hit_rate": 0.8,
                "rerank_hit_rate": 0.7,
                "rerank_rejection_accuracy": 1.0,
            }
        else:
            metrics = {
                "candidate_hit_rate": 0.9,
                "rerank_hit_rate": 0.8,
                "rerank_rejection_accuracy": 1.0,
            }
        return {"metrics": metrics}

    monkeypatch.setattr(rerank_module, "evaluate_rerank", fake_evaluate_rerank)

    results = rerank_module.compare_rerank_retrieval_modes(
        candidate_k=6,
        rerank_top_k=3,
        rerank_min_score=0.75,
        keyword_limit=5,
    )

    assert captured_modes == ["vector", "hybrid"]
    assert results[0]["retrieval_mode"] == "vector"
    assert results[0]["is_recommended"] is False
    assert results[1]["retrieval_mode"] == "hybrid"
    assert results[1]["is_recommended"] is True


def test_run_rerank_retrieval_mode_comparison_saves_result(monkeypatch, capsys):
    captured = {}
    comparison_results = [
        {
            "retrieval_mode": "vector",
            "candidate_hit_rate": 0.8,
        },
        {
            "retrieval_mode": "hybrid",
            "candidate_hit_rate": 0.9,
        },
    ]

    def fake_compare_rerank_retrieval_modes(
            candidate_k,
            rerank_top_k,
            rerank_min_score,
            keyword_limit,
    ):
        captured["comparison_args"] = {
            "candidate_k": candidate_k,
            "rerank_top_k": rerank_top_k,
            "rerank_min_score": rerank_min_score,
            "keyword_limit": keyword_limit,
        }
        return comparison_results

    def fake_save_evaluation_result(result, filename_prefix, result_type):
        captured["saved"] = {
            "result": result,
            "filename_prefix": filename_prefix,
            "result_type": result_type,
        }
        return "evaluation_results/result.json"

    monkeypatch.setattr(
        rerank_module,
        "compare_rerank_retrieval_modes",
        fake_compare_rerank_retrieval_modes,
    )
    monkeypatch.setattr(
        rerank_module,
        "save_evaluation_result",
        fake_save_evaluation_result,
    )

    result = rerank_module.run_rerank_retrieval_mode_comparison(
        candidate_k=6,
        rerank_top_k=3,
        rerank_min_score=0.75,
        keyword_limit=5,
    )

    output = capsys.readouterr().out

    assert result == comparison_results
    assert captured["comparison_args"] == {
        "candidate_k": 6,
        "rerank_top_k": 3,
        "rerank_min_score": 0.75,
        "keyword_limit": 5,
    }
    assert captured["saved"] == {
        "result": comparison_results,
        "filename_prefix": "rerank_retrieval_mode_comparison",
        "result_type": "comparison",
    }
    assert "Saved to: evaluation_results/result.json" in output
