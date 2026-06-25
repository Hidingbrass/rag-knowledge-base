import json
from pathlib import Path
from tempfile import TemporaryDirectory

from app.evaluation.utils import (
    average_metric,
    calculate_rate,
    calculate_chunk_support_rate,
    evaluate_citations,
    is_rejection_answer,
    mark_recommended_rerank_config,
    mark_recommended_rerank_min_score,
    mark_recommended_rerank_retrieval_mode,
    parse_float_list,
    parse_int_list,
    save_evaluation_result,
)


def test_calculate_chunk_support_rate_full_support():
    result = calculate_chunk_support_rate(
        ["Redis", "缓存", "限流"],
        [{"text": "Redis 可以用于缓存热点数据，也可以实现简单限流。"}],
    )

    assert result["supported_keywords"] == ["Redis", "缓存", "限流"]
    assert result["missing_keywords"] == []
    assert result["support_rate"] == 1.0


def test_calculate_chunk_support_rate_partial_support():
    result = calculate_chunk_support_rate(
        ["Redis", "缓存", "消息队列"],
        [{"text": "Redis 可以用于缓存热点数据。"}],
    )

    assert result["supported_keywords"] == ["Redis", "缓存"]
    assert result["missing_keywords"] == ["消息队列"]
    assert result["support_rate"] == 2 / 3


def test_calculate_chunk_support_rate_empty_keywords():
    result = calculate_chunk_support_rate(
        [],
        [{"text": "Redis 可以用于缓存热点数据。"}],
    )

    assert result["supported_keywords"] == []
    assert result["missing_keywords"] == []
    assert result["support_rate"] == 0.0


def test_evaluate_citations_valid_repeated_citations():
    sources = [
        {"text": "first source"},
        {"text": "second source"},
    ]

    result = evaluate_citations("Answer [1] and [1], also [2].", sources)

    assert result["citation_numbers"] == [1, 1, 2]
    assert result["valid_citations"] == [1, 1, 2]
    assert result["unique_valid_citations"] == [1, 2]
    assert result["citations_valid"] is True
    assert result["cited_sources"] == sources


def test_evaluate_citations_invalid_out_of_range_citation():
    sources = [
        {"text": "first source"},
    ]

    result = evaluate_citations("Answer [1] and [2].", sources)

    assert result["citation_numbers"] == [1, 2]
    assert result["valid_citations"] == [1]
    assert result["unique_valid_citations"] == [1]
    assert result["citations_valid"] is False
    assert result["cited_sources"] == [sources[0]]


def test_evaluate_citations_no_citation():
    sources = [
        {"text": "first source"},
    ]

    result = evaluate_citations("Answer without citation.", sources)

    assert result["citation_numbers"] == []
    assert result["valid_citations"] == []
    assert result["unique_valid_citations"] == []
    assert result["citations_valid"] is False
    assert result["cited_sources"] == []


def test_is_rejection_answer_detects_known_phrase():
    assert is_rejection_answer("资料没有提及这个问题，无法确定。") is True


def test_is_rejection_answer_returns_false_without_known_phrase():
    assert is_rejection_answer("Redis 可以用于缓存热点数据。") is False


def test_average_metric_calculates_average_value():
    metrics_list = [
        {"answer_pass_rate": 1.0},
        {"answer_pass_rate": 0.5},
    ]

    assert average_metric(metrics_list, "answer_pass_rate") == 0.75


def test_average_metric_empty_list_returns_zero():
    assert average_metric([], "answer_pass_rate") == 0.0


def test_calculate_rate_with_non_zero_denominator():
    assert calculate_rate(3, 4) == 0.75


def test_calculate_rate_with_zero_denominator():
    assert calculate_rate(3, 0) == 0.0


def test_parse_int_list():
    assert parse_int_list("1, 3,5") == [1, 3, 5]


def test_parse_float_list():
    assert parse_float_list("0.72, 0.75,0.78") == [0.72, 0.75, 0.78]


def test_mark_recommended_rerank_config_selects_best_qualified_summary():
    summaries = [
        {
            "candidate_k": 8,
            "rerank_top_k": 4,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
            "fallback_rate": 0.0,
            "slow_rerank_rate": 0.0,
            "average_rerank_elapsed_seconds": 0.3,
        },
        {
            "candidate_k": 6,
            "rerank_top_k": 3,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
            "fallback_rate": 0.0,
            "slow_rerank_rate": 0.0,
            "average_rerank_elapsed_seconds": 0.5,
        },
        {
            "candidate_k": 6,
            "rerank_top_k": 3,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 0.875,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
            "fallback_rate": 0.0,
            "slow_rerank_rate": 0.0,
            "average_rerank_elapsed_seconds": 0.1,
        },
    ]

    result = mark_recommended_rerank_config(summaries)

    assert result[0]["is_recommended"] is False
    assert result[1]["is_recommended"] is True
    assert result[2]["is_recommended"] is False


def test_mark_recommended_rerank_config_marks_none_when_no_summary_qualified():
    summaries = [
        {
            "candidate_k": 6,
            "rerank_top_k": 3,
            "answer_pass_rate": 0.9,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
            "fallback_rate": 0.0,
            "slow_rerank_rate": 0.0,
            "average_rerank_elapsed_seconds": 0.5,
        },
    ]

    result = mark_recommended_rerank_config(summaries)

    assert result[0]["is_recommended"] is False


def test_mark_recommended_rerank_config_requires_chunk_support():
    summaries = [
        {
            "candidate_k": 6,
            "rerank_top_k": 3,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 0.9,
            "fallback_rate": 0.0,
            "slow_rerank_rate": 0.0,
            "average_rerank_elapsed_seconds": 0.5,
        },
    ]

    result = mark_recommended_rerank_config(summaries)

    assert result[0]["is_recommended"] is False


def test_mark_recommended_rerank_min_score_selects_lowest_qualified_score():
    summaries = [
        {
            "rerank_min_score": 0.72,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 0.875,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
        {
            "rerank_min_score": 0.75,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
        {
            "rerank_min_score": 0.78,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
    ]

    result = mark_recommended_rerank_min_score(summaries)

    assert result[0]["is_recommended"] is False
    assert result[1]["is_recommended"] is True
    assert result[2]["is_recommended"] is False


def test_mark_recommended_rerank_min_score_marks_none_when_no_score_qualified():
    summaries = [
        {
            "rerank_min_score": 0.72,
            "answer_pass_rate": 0.9,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 1.0,
        },
    ]

    result = mark_recommended_rerank_min_score(summaries)

    assert result[0]["is_recommended"] is False


def test_mark_recommended_rerank_min_score_requires_chunk_support():
    summaries = [
        {
            "rerank_min_score": 0.75,
            "answer_pass_rate": 1.0,
            "rejection_accuracy": 1.0,
            "citation_valid_rate": 1.0,
            "average_citation_support": 1.0,
            "chunk_support_pass_rate": 0.9,
        },
    ]

    result = mark_recommended_rerank_min_score(summaries)

    assert result[0]["is_recommended"] is False


def test_mark_recommended_rerank_retrieval_mode_prefers_better_rerank_hit_rate():
    summaries = [
        {
            "retrieval_mode": "vector",
            "candidate_hit_rate": 0.8,
            "rerank_hit_rate": 0.7,
            "rerank_rejection_accuracy": 1.0,
        },
        {
            "retrieval_mode": "hybrid",
            "candidate_hit_rate": 0.9,
            "rerank_hit_rate": 0.8,
            "rerank_rejection_accuracy": 1.0,
        },
    ]

    result = mark_recommended_rerank_retrieval_mode(summaries)

    assert result[0]["is_recommended"] is False
    assert result[1]["is_recommended"] is True


def test_mark_recommended_rerank_retrieval_mode_prefers_vector_when_tied():
    summaries = [
        {
            "retrieval_mode": "vector",
            "candidate_hit_rate": 0.8,
            "rerank_hit_rate": 0.8,
            "rerank_rejection_accuracy": 1.0,
        },
        {
            "retrieval_mode": "hybrid",
            "candidate_hit_rate": 0.8,
            "rerank_hit_rate": 0.8,
            "rerank_rejection_accuracy": 1.0,
        },
    ]

    result = mark_recommended_rerank_retrieval_mode(summaries)

    assert result[0]["is_recommended"] is True
    assert result[1]["is_recommended"] is False


def test_save_evaluation_result_creates_json_file():
    result = [
        {
            "rerank_min_score": 0.75,
            "answer_pass_rate": 1.0,
        }
    ]

    with TemporaryDirectory(dir=Path.cwd()) as temp_dir:
        saved_path = save_evaluation_result(
            result,
            "test_experiment",
            "comparison",
            temp_dir,
        )

        assert saved_path.exists()

        with saved_path.open("r", encoding="utf-8") as file:
            payload = json.load(file)

        assert payload["experiment_name"] == "test_experiment"
        assert "created_at" in payload
        assert payload["results"] == result
        assert payload["result_type"] == "comparison"
