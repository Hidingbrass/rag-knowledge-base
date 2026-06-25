import argparse

from app.evaluation.cases import TEST_CASES
from app.core.config import settings
from app.evaluation.utils import calculate_rate, save_evaluation_result
from app.services.qdrant_service import search_chunks


def evaluate_retrieval(
        top_k: int = settings.default_top_k,
        min_score: float = settings.default_min_score,
):
    results = []
    hit_count = 0
    normal_count = 0
    rejection_correct = 0
    rejection_count = 0
    for case in TEST_CASES:
        sources = search_chunks(case["question"], top_k)

        matched_sources = [
            source
            for source in sources
            if source["vector_score"] >= min_score
        ]
        if case["should_reject"]:
            rejection_count += 1
            rejection_passed = len(matched_sources) == 0

            if rejection_passed:
                rejection_correct += 1

            results.append(
                {
                    "question": case["question"],
                    "type": "rejection",
                    "passed": rejection_passed,
                    "matched_count": len(matched_sources),
                }
            )

            continue

        normal_count += 1
        retrieved_pages = [
            source["page_number"]
            for source in matched_sources
        ]
        hit = case["expected_page"] in retrieved_pages
        if hit:
            hit_count += 1

        results.append(
            {
                "question": case["question"],
                "type": "retrieval",
                "passed": hit,
                "expected_page": case["expected_page"],
                "retrieved_pages": retrieved_pages,
                "scores": [
                    source["vector_score"]
                    for source in matched_sources
                ],
            }
        )

    hit_rate = calculate_rate(hit_count, normal_count)

    rejection_accuracy = calculate_rate(rejection_correct, rejection_count)

    return {
        "config": {
            "top_k": top_k,
            "min_score": min_score,
        },
        "metrics": {
            "hit_at_k": round(hit_rate, 4),
            "rejection_accuracy": round(rejection_accuracy, 4),
            "hit_count": hit_count,
            "normal_count": normal_count,
            "rejection_correct": rejection_correct,
            "rejection_count": rejection_count,
        },
        "results": results,
    }


def run_retrieval_evaluation(top_k, min_score):
    evaluation = evaluate_retrieval(
        top_k=top_k,
        min_score=min_score,
    )

    print(evaluation["metrics"])

    saved_path = save_evaluation_result(
        evaluation,
        "retrieval",
        result_type="single_evaluation",
    )

    print(f"Saved to: {saved_path}")

    return evaluation


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Run vector retrieval evaluation.",
    )

    parser.add_argument(
        "--top-k",
        type=int,
        default=settings.default_top_k,
        help="Number of chunks to retrieve.",
    )

    parser.add_argument(
        "--min-score",
        type=float,
        default=settings.default_min_score,
        help="Minimum vector score for matched sources.",
    )

    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()

    run_retrieval_evaluation(
        top_k=args.top_k,
        min_score=args.min_score,
    )
