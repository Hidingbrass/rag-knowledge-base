import argparse

from app.evaluation.cases import TEST_CASES
from app.core.config import settings
from app.evaluation.utils import (
    calculate_rate,
    mark_recommended_rerank_retrieval_mode,
    save_evaluation_result,
)
from app.services.rerank_service import rerank_chunks
from app.services.qdrant_service import search_chunks, hybrid_search_chunks


def evaluate_rerank(
        candidate_k: int = settings.default_candidate_k,
        rerank_top_k: int = settings.default_rerank_top_k,
        rerank_min_score: float = settings.default_rerank_min_score,
        retrieval_mode: str = "vector",
        keyword_limit: int = 6,
):
    results = []
    normal_count = 0
    candidate_hit_count = 0
    rerank_hit_count = 0
    rerank_normal_passed = 0  # 普通题最高分达到阈值的数量
    rerank_rejection_correct = 0  # 拒答题最高分低于阈值的数量
    rerank_rejection_count = 0  # 拒答题总数

    for case in TEST_CASES:
        if retrieval_mode == "vector":
            candidate_sources = search_chunks(case["question"], candidate_k)
        elif retrieval_mode == "hybrid":
            candidate_sources = hybrid_search_chunks(
                case["question"],
                candidate_k,
                keyword_limit,
            )
        else:
            raise ValueError("retrieval_mode must be 'vector' or 'hybrid'")
        reranked_sources = rerank_chunks(case["question"], candidate_sources, rerank_top_k)
        rerank_scores = [
            source["rerank_score"]
            for source in reranked_sources
        ]
        top_rerank_score = (
            rerank_scores[0]
            if rerank_scores
            else None
        )
        vector_scores = [
            source.get("vector_score")
            for source in candidate_sources
        ]
        keyword_scores = [
            source.get("keyword_score")
            for source in candidate_sources
        ]
        if case["should_reject"]:
            rerank_rejection_count += 1
            if top_rerank_score is None or top_rerank_score < rerank_min_score:
                rerank_rejection_correct += 1
            results.append(
                {
                    "question": case["question"],
                    "type": "rejection",
                    "top_rerank_score": top_rerank_score,
                    "vector_scores": vector_scores,
                    "keyword_scores": keyword_scores,
                    "rerank_scores": rerank_scores,
                }
            )
            continue

        normal_count += 1
        if top_rerank_score is not None and top_rerank_score >= rerank_min_score:
            rerank_normal_passed += 1
        candidate_pages = [
            source["page_number"]
            for source in candidate_sources
        ]

        candidate_hit = case["expected_page"] in candidate_pages
        if candidate_hit:
            candidate_hit_count += 1

        reranked_pages = [
            source["page_number"]
            for source in reranked_sources
        ]

        rerank_hit = case["expected_page"] in reranked_pages
        if rerank_hit:
            rerank_hit_count += 1
        results.append({
            "question": case["question"],
            "type": "retrieval",
            "expected_page": case["expected_page"],
            "candidate_hit": candidate_hit,
            "rerank_hit": rerank_hit,
            "candidate_pages": candidate_pages,
            "reranked_pages": reranked_pages,
            "vector_scores": vector_scores,
            "keyword_scores": keyword_scores,
            "rerank_scores": rerank_scores,
        })

    candidate_hit_rate = calculate_rate(candidate_hit_count, normal_count)
    rerank_hit_rate = calculate_rate(rerank_hit_count, normal_count)
    rerank_normal_pass_rate = calculate_rate(rerank_normal_passed, normal_count)
    rerank_rejection_accuracy = calculate_rate(
        rerank_rejection_correct,
        rerank_rejection_count,
    )

    return {
        "config": {
            "candidate_k": candidate_k,
            "rerank_top_k": rerank_top_k,
            "rerank_min_score": rerank_min_score,
            "retrieval_mode": retrieval_mode,
            "keyword_limit": keyword_limit,
        },
        "metrics": {
            "candidate_hit_rate": round(candidate_hit_rate, 4),
            "rerank_hit_rate": round(rerank_hit_rate, 4),
            "candidate_hit_count": candidate_hit_count,
            "rerank_hit_count": rerank_hit_count,
            "normal_count": normal_count,
            "rerank_normal_pass_rate": round(rerank_normal_pass_rate, 4),
            "rerank_normal_passed": rerank_normal_passed,
            "rerank_rejection_accuracy": round(rerank_rejection_accuracy, 4),
            "rerank_rejection_correct": rerank_rejection_correct,
            "rerank_rejection_count": rerank_rejection_count,
        },
        "results": results,
    }


def run_rerank_retrieval_evaluation(
        candidate_k,
        rerank_top_k,
        rerank_min_score,
        retrieval_mode,
        keyword_limit,
):
    evaluation = evaluate_rerank(
        candidate_k=candidate_k,
        rerank_top_k=rerank_top_k,
        rerank_min_score=rerank_min_score,
        retrieval_mode=retrieval_mode,
        keyword_limit=keyword_limit,
    )

    print(evaluation["metrics"])

    saved_path = save_evaluation_result(
        evaluation,
        "rerank_retrieval",
        result_type="single_evaluation",
    )

    print(f"Saved to: {saved_path}")

    return evaluation


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Run Rerank retrieval evaluation.",
    )

    parser.add_argument(
        "--candidate-k",
        type=int,
        default=settings.default_candidate_k,
        help="Number of vector candidates before rerank.",
    )

    parser.add_argument(
        "--rerank-top-k",
        type=int,
        default=settings.default_rerank_top_k,
        help="Number of reranked chunks kept after rerank.",
    )

    parser.add_argument(
        "--rerank-min-score",
        type=float,
        default=settings.default_rerank_min_score,
        help="Minimum top rerank score used for rejection judgment.",
    )

    parser.add_argument(
        "--retrieval-mode",
        choices=["vector", "hybrid"],
        default="vector",
        help="Candidate retrieval mode before rerank.",
    )

    parser.add_argument(
        "--keyword-limit",
        type=int,
        default=6,
        help="Number of keyword candidates in hybrid mode.",
    )

    parser.add_argument(
        "--mode",
        choices=["single", "comparison"],
        default="single",
        help="Run one evaluation or compare vector and hybrid retrieval modes.",
    )

    return parser.parse_args(argv)


def compare_rerank_retrieval_modes(
        candidate_k=settings.default_candidate_k,
        rerank_top_k=settings.default_rerank_top_k,
        rerank_min_score=settings.default_rerank_min_score,
        keyword_limit=6,
):
    comparison_results = []

    for mode in ["vector", "hybrid"]:
        evaluation = evaluate_rerank(
            candidate_k=candidate_k,
            rerank_top_k=rerank_top_k,
            rerank_min_score=rerank_min_score,
            retrieval_mode=mode,
            keyword_limit=keyword_limit,
        )

        metrics = evaluation["metrics"]

        comparison_results.append({
            "retrieval_mode": mode,
            "candidate_k": candidate_k,
            "rerank_top_k": rerank_top_k,
            "rerank_min_score": rerank_min_score,
            "keyword_limit": keyword_limit,
            "candidate_hit_rate": metrics["candidate_hit_rate"],
            "rerank_hit_rate": metrics["rerank_hit_rate"],
            "rerank_rejection_accuracy": metrics["rerank_rejection_accuracy"],
        })

    return mark_recommended_rerank_retrieval_mode(comparison_results)


def run_rerank_retrieval_mode_comparison(
        candidate_k,
        rerank_top_k,
        rerank_min_score,
        keyword_limit,
):
    comparison_results = compare_rerank_retrieval_modes(
        candidate_k=candidate_k,
        rerank_top_k=rerank_top_k,
        rerank_min_score=rerank_min_score,
        keyword_limit=keyword_limit,
    )

    for result in comparison_results:
        print(result)

    saved_path = save_evaluation_result(
        comparison_results,
        "rerank_retrieval_mode_comparison",
        result_type="comparison",
    )

    print(f"Saved to: {saved_path}")

    return comparison_results


if __name__ == "__main__":
    args = parse_args()

    if args.mode == "single":
        run_rerank_retrieval_evaluation(
            candidate_k=args.candidate_k,
            rerank_top_k=args.rerank_top_k,
            rerank_min_score=args.rerank_min_score,
            retrieval_mode=args.retrieval_mode,
            keyword_limit=args.keyword_limit,
        )
    else:
        run_rerank_retrieval_mode_comparison(
            candidate_k=args.candidate_k,
            rerank_top_k=args.rerank_top_k,
            rerank_min_score=args.rerank_min_score,
            keyword_limit=args.keyword_limit,
        )
