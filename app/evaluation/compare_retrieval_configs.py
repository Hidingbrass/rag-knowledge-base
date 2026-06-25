import argparse

from app.evaluation.evaluate_retrieval import evaluate_retrieval
from app.evaluation.utils import parse_float_list, parse_int_list, save_evaluation_result

TOP_K_VALUES = [1, 3, 5]
MIN_SCORE_VALUES = [0.45, 0.55, 0.65]


def compare_configs(
        top_k_values=None,
        min_score_values=None,
):
    if top_k_values is None:
        top_k_values = TOP_K_VALUES

    if min_score_values is None:
        min_score_values = MIN_SCORE_VALUES

    results = []
    for top_k_value in top_k_values:
        for min_score_value in min_score_values:
            evaluation = evaluate_retrieval(top_k_value, min_score_value)
            metrics = evaluation["metrics"]
            results.append({
                "top_k": top_k_value,
                "min_score": min_score_value,
                "hit_at_k": metrics["hit_at_k"],
                "rejection_accuracy": metrics["rejection_accuracy"],
            })
    return results


def run_retrieval_config_comparison(top_k_values, min_score_values):
    results = compare_configs(
        top_k_values=top_k_values,
        min_score_values=min_score_values,
    )

    for result in results:
        print(result)

    saved_path = save_evaluation_result(
        results,
        "retrieval_config_comparison",
        result_type="comparison",
    )

    print(f"Saved to: {saved_path}")

    return results


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Compare vector retrieval parameter configs.",
    )

    parser.add_argument(
        "--top-k-values",
        type=parse_int_list,
        default=TOP_K_VALUES,
        help="Comma-separated top_k values, for example: 1,3,5.",
    )

    parser.add_argument(
        "--min-score-values",
        type=parse_float_list,
        default=MIN_SCORE_VALUES,
        help="Comma-separated min_score values, for example: 0.45,0.55,0.65.",
    )

    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()

    run_retrieval_config_comparison(
        top_k_values=args.top_k_values,
        min_score_values=args.min_score_values,
    )
