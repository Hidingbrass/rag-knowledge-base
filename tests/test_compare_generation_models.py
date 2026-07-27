from app.evaluation.compare_generation_models import (
    compare_generation_models,
    parse_args,
    parse_models,
)


def test_parse_models_deduplicates_and_rejects_empty():
    assert parse_models("qwen-plus, qwen-max,qwen-plus") == ["qwen-plus", "qwen-max"]


def test_model_comparison_uses_same_cases_and_aggregates_metrics(monkeypatch):
    cases = [{
        "id": "case-1",
        "category": "semantic",
        "question": "什么是 RAG？",
        "gold_documents": ["rag.pdf"],
        "expected_keywords": ["检索"],
        "should_reject": False,
    }]
    monkeypatch.setattr(
        "app.evaluation.compare_generation_models.load_evaluation_cases",
        lambda dataset_name: cases,
    )
    seen = []

    def fake_evaluator(**kwargs):
        seen.append((kwargs["generation_model"], kwargs["cases"]))
        return {"metrics": {
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "rejection_precision": 1.0,
            "rejection_recall": 1.0,
            "rejection_f1": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
            "fallback_rate": 0.0,
            "average_rerank_elapsed_seconds": 0.2,
            "total_tokens": 100,
            "estimated_cost_yuan": 0.01,
        }}

    result = compare_generation_models(
        ["model-a", "model-b"],
        dataset_name="technical_docs",
        repeat_count=2,
        evaluator=fake_evaluator,
    )

    assert len(seen) == 4
    assert all(seen_cases is cases for _, seen_cases in seen)
    assert result["case_count"] == 1
    assert result["comparison"][0]["average_total_tokens"] == 100
    assert result["comparison"][1]["model"] == "model-b"


def test_model_comparison_cli_accepts_models_and_dataset():
    args = parse_args([
        "--models", "model-a,model-b",
        "--dataset", "legacy",
        "--repeat-count", "2",
    ])

    assert args.models == ["model-a", "model-b"]
    assert args.dataset == "legacy"
    assert args.repeat_count == 2
