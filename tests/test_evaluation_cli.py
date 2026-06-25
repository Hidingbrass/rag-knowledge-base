from app.core.config import settings
from app.evaluation.compare_retrieval_configs import (
    MIN_SCORE_VALUES,
    TOP_K_VALUES,
    parse_args as parse_retrieval_comparison_args,
)
from app.evaluation.evaluate_generation import parse_args as parse_generation_args
from app.evaluation.evaluate_generation_rerank import parse_args as parse_rerank_args
from app.evaluation.evaluate_rerank import parse_args as parse_rerank_retrieval_args
from app.evaluation.evaluate_rerank_params import parse_args as parse_rerank_params_args
from app.evaluation.evaluate_retrieval import parse_args as parse_retrieval_args


def test_generation_parse_args_uses_defaults():
    args = parse_generation_args([])

    assert args.top_k == settings.default_top_k
    assert args.min_score == settings.default_min_score


def test_generation_parse_args_accepts_custom_values():
    args = parse_generation_args(["--top-k", "5", "--min-score", "0.5"])

    assert args.top_k == 5
    assert args.min_score == 0.5


def test_rerank_parse_args_uses_defaults():
    args = parse_rerank_args([])

    assert args.mode == "comparison"
    assert args.candidate_k == settings.default_candidate_k
    assert args.rerank_top_k == settings.default_rerank_top_k
    assert args.rerank_min_score == settings.default_rerank_min_score
    assert args.retrieval_mode == "vector"
    assert args.keyword_limit == 6
    assert args.repeat_count == 3
    assert args.rerank_min_score_values == [0.72, 0.75, 0.78]


def test_rerank_parse_args_accepts_custom_values():
    args = parse_rerank_args(
        [
            "--mode",
            "single",
            "--candidate-k",
            "8",
            "--rerank-top-k",
            "4",
            "--rerank-min-score",
            "0.72",
            "--repeat-count",
            "2",
            "--rerank-min-score-values",
            "0.7,0.8",
        ]
    )

    assert args.mode == "single"
    assert args.candidate_k == 8
    assert args.rerank_top_k == 4
    assert args.rerank_min_score == 0.72
    assert args.repeat_count == 2
    assert args.rerank_min_score_values == [0.7, 0.8]


def test_retrieval_parse_args_uses_defaults():
    args = parse_retrieval_args([])

    assert args.top_k == settings.default_top_k
    assert args.min_score == settings.default_min_score


def test_retrieval_parse_args_accepts_custom_values():
    args = parse_retrieval_args(["--top-k", "5", "--min-score", "0.5"])

    assert args.top_k == 5
    assert args.min_score == 0.5


def test_rerank_retrieval_parse_args_uses_defaults():
    args = parse_rerank_retrieval_args([])

    assert args.candidate_k == settings.default_candidate_k
    assert args.rerank_top_k == settings.default_rerank_top_k
    assert args.rerank_min_score == settings.default_rerank_min_score
    assert args.mode == "single"


def test_rerank_retrieval_parse_args_accepts_custom_values():
    args = parse_rerank_retrieval_args(
        [
            "--candidate-k",
            "8",
            "--rerank-top-k",
            "4",
            "--rerank-min-score",
            "0.72",
            "--retrieval-mode",
            "hybrid",
            "--keyword-limit",
            "5",
            "--mode",
            "comparison",
        ]
    )

    assert args.candidate_k == 8
    assert args.rerank_top_k == 4
    assert args.rerank_min_score == 0.72
    assert args.retrieval_mode == "hybrid"
    assert args.keyword_limit == 5
    assert args.mode == "comparison"


def test_retrieval_comparison_parse_args_uses_defaults():
    args = parse_retrieval_comparison_args([])

    assert args.top_k_values == TOP_K_VALUES
    assert args.min_score_values == MIN_SCORE_VALUES


def test_retrieval_comparison_parse_args_accepts_custom_values():
    args = parse_retrieval_comparison_args(
        [
            "--top-k-values",
            "1,3",
            "--min-score-values",
            "0.45,0.55",
        ]
    )

    assert args.top_k_values == [1, 3]
    assert args.min_score_values == [0.45, 0.55]


def test_rerank_params_parse_args_uses_defaults():
    args = parse_rerank_params_args([])

    assert args.repeat_count == 3


def test_rerank_params_parse_args_accepts_custom_values():
    args = parse_rerank_params_args(["--repeat-count", "2"])

    assert args.repeat_count == 2
