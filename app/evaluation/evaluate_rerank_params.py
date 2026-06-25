import argparse

from app.evaluation.evaluate_generation_rerank import evaluate_generation_rerank
from app.evaluation.utils import (
    average_metric,
    mark_recommended_rerank_config,
    save_evaluation_result,
)

PARAMETER_SETS = [
    {"candidate_k": 6, "rerank_top_k": 3, "rerank_min_score": 0.75},
    {"candidate_k": 8, "rerank_top_k": 3, "rerank_min_score": 0.75},
    {"candidate_k": 8, "rerank_top_k": 4, "rerank_min_score": 0.75},
]


def evaluate_rerank_params(repeat_count: int = 3):
    summaries = []
    for params in PARAMETER_SETS:
        metrics_list = []
        for _ in range(repeat_count):
            evaluation = evaluate_generation_rerank(
                candidate_k=params["candidate_k"],
                rerank_top_k=params["rerank_top_k"],
                rerank_min_score=params["rerank_min_score"],
            )
            metrics = evaluation["metrics"]
            metrics_list.append(metrics)

        summary = {
            "candidate_k": params["candidate_k"],
            "rerank_top_k": params["rerank_top_k"],
            "rerank_min_score": params["rerank_min_score"],
            "repeat_count": repeat_count,
            "answer_pass_rate": round(average_metric(metrics_list, "answer_pass_rate"), 4),
            "rejection_accuracy": round(average_metric(metrics_list, "rejection_accuracy"), 4),
            "citation_valid_rate": round(average_metric(metrics_list, "citation_valid_rate"), 4),
            "average_citation_support": round(average_metric(metrics_list, "average_citation_support"), 4),
            "chunk_support_pass_rate": round(average_metric(metrics_list, "chunk_support_pass_rate"), 4),
            "fallback_rate": round(average_metric(metrics_list, "fallback_rate"), 4),
            "average_rerank_elapsed_seconds": round(average_metric(metrics_list, "average_rerank_elapsed_seconds"), 4),
            "slow_rerank_rate": round(average_metric(metrics_list, "slow_rerank_rate"), 4),
        }
        summaries.append(summary)

    return mark_recommended_rerank_config(summaries)


def run_rerank_params_evaluation(repeat_count):
    summaries = evaluate_rerank_params(repeat_count=repeat_count)

    for summary in summaries:
        print(summary)

    saved_path = save_evaluation_result(
        summaries,
        "rerank_params_comparison",
        result_type="comparison",
    )

    print(f"Saved to: {saved_path}")

    return summaries


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Compare Rerank generation parameter configs.",
    )

    parser.add_argument(
        "--repeat-count",
        type=int,
        default=3,
        help="Repeat count for each parameter set.",
    )

    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()

    run_rerank_params_evaluation(
        repeat_count=args.repeat_count,
    )
