"""Ablation study for dense, sparse, Hybrid RRF and Hybrid RRF + Rerank."""

from __future__ import annotations

import argparse
import time

from app.core.config import settings
from app.evaluation.cases import TEST_CASES
from app.evaluation.datasets import DATASET_NAMES, load_evaluation_cases
from app.evaluation.utils import calculate_rate, save_evaluation_result
from app.services.qdrant_service import (
    hybrid_search_chunks,
    search_chunks,
    sparse_search_chunks,
)
from app.services.rerank_service import rerank_chunks


RETRIEVAL_MODES = ("vector", "sparse", "hybrid", "hybrid_rerank")


def percentile(values: list[float], quantile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * quantile)))
    return ordered[index]


def score_retrieved_sources(case: dict, sources: list[dict]) -> dict:
    """Score either the legacy gold page or the technical-doc gold filenames."""
    retrieved_pages = [source.get("page_number") for source in sources]
    retrieved_documents = [source.get("filename") for source in sources]
    gold_documents = case.get("gold_documents") or []

    if gold_documents:
        gold_set = set(gold_documents)
        retrieved_set = {
            filename for filename in retrieved_documents if filename is not None
        }
        matching_ranks = [
            rank
            for rank, filename in enumerate(retrieved_documents, start=1)
            if filename in gold_set
        ]
        document_recall = calculate_rate(
            len(gold_set & retrieved_set),
            len(gold_set),
        )
        return {
            "hit": bool(matching_ranks),
            "full_gold_hit": document_recall == 1.0,
            "reciprocal_rank": 1 / matching_ranks[0] if matching_ranks else 0.0,
            "gold_document_recall": document_recall,
            "retrieved_pages": retrieved_pages,
            "retrieved_documents": retrieved_documents,
        }

    expected_page = case.get("expected_page")
    matching_ranks = [
        rank
        for rank, page in enumerate(retrieved_pages, start=1)
        if page == expected_page
    ]
    return {
        "hit": expected_page in retrieved_pages,
        "full_gold_hit": expected_page in retrieved_pages,
        "reciprocal_rank": 1 / matching_ranks[0] if matching_ranks else 0.0,
        "gold_document_recall": 1.0 if expected_page in retrieved_pages else 0.0,
        "retrieved_pages": retrieved_pages,
        "retrieved_documents": retrieved_documents,
    }


def retrieve_for_mode(
        mode: str,
        question: str,
        candidate_k: int,
        sparse_limit: int,
        rerank_top_k: int,
        rerank_document_diversity: bool = settings.rerank_document_diversity_enabled,
) -> list[dict]:
    if mode == "vector":
        return search_chunks(question, candidate_k)
    if mode == "sparse":
        return sparse_search_chunks(question, sparse_limit)

    hybrid_sources = hybrid_search_chunks(question, candidate_k, sparse_limit)
    if mode == "hybrid":
        return hybrid_sources
    if mode == "hybrid_rerank":
        return rerank_chunks(
            question,
            hybrid_sources,
            rerank_top_k,
            rerank_document_diversity,
        )
    raise ValueError(f"unsupported retrieval mode: {mode}")


def evaluate_retrieval_mode(
        mode: str,
        candidate_k: int = settings.default_candidate_k,
        sparse_limit: int = settings.default_sparse_limit,
        rerank_top_k: int = settings.default_rerank_top_k,
        cases: list[dict] | None = None,
        dataset_name: str = "legacy",
        rerank_document_diversity: bool = settings.rerank_document_diversity_enabled,
) -> dict:
    if mode not in RETRIEVAL_MODES:
        raise ValueError(f"mode must be one of: {', '.join(RETRIEVAL_MODES)}")

    results = []
    normal_count = 0
    hit_count = 0
    elapsed_values = []
    reciprocal_ranks = []
    gold_document_recalls = []
    full_gold_hit_count = 0
    selected_cases = TEST_CASES if cases is None else cases

    for case in selected_cases:
        started_at = time.perf_counter()
        sources = retrieve_for_mode(
            mode,
            case["question"],
            candidate_k,
            sparse_limit,
            rerank_top_k,
            rerank_document_diversity,
        )
        elapsed_ms = (time.perf_counter() - started_at) * 1000
        elapsed_values.append(elapsed_ms)

        hit = None
        source_score = score_retrieved_sources(case, sources)
        if not case["should_reject"]:
            normal_count += 1
            hit = source_score["hit"]
            if hit:
                hit_count += 1
            if source_score["full_gold_hit"]:
                full_gold_hit_count += 1
            reciprocal_ranks.append(source_score["reciprocal_rank"])
            gold_document_recalls.append(source_score["gold_document_recall"])

        results.append({
            "case_id": case.get("id"),
            "category": case.get("category"),
            "question": case["question"],
            "should_reject": case["should_reject"],
            "expected_page": case.get("expected_page"),
            "gold_documents": case.get("gold_documents", []),
            "retrieved_pages": source_score["retrieved_pages"],
            "retrieved_documents": source_score["retrieved_documents"],
            "hit": hit,
            "full_gold_hit": source_score["full_gold_hit"] if hit is not None else None,
            "reciprocal_rank": (
                round(source_score["reciprocal_rank"], 4)
                if hit is not None else None
            ),
            "gold_document_recall": (
                round(source_score["gold_document_recall"], 4)
                if hit is not None else None
            ),
            "elapsed_ms": round(elapsed_ms, 2),
            "scores": [
                {
                    "vector_score": source.get("vector_score"),
                    "sparse_score": source.get("sparse_score"),
                    "fusion_score": source.get("fusion_score"),
                    "rerank_score": source.get("rerank_score"),
                }
                for source in sources
            ],
        })

    return {
        "config": {
            "dataset": dataset_name,
            "mode": mode,
            "candidate_k": candidate_k,
            "sparse_limit": sparse_limit,
            "rerank_top_k": rerank_top_k,
            "result_k": (
                rerank_top_k
                if mode == "hybrid_rerank"
                else sparse_limit if mode == "sparse" else candidate_k
            ),
            "rerank_document_diversity": rerank_document_diversity,
        },
        "metrics": {
            "hit_at_k": round(calculate_rate(hit_count, normal_count), 4),
            "hit_count": hit_count,
            "normal_count": normal_count,
            "mrr_at_k": round(
                calculate_rate(sum(reciprocal_ranks), len(reciprocal_ranks)),
                4,
            ),
            "mean_gold_document_recall": round(
                calculate_rate(
                    sum(gold_document_recalls),
                    len(gold_document_recalls),
                ),
                4,
            ),
            "full_gold_hit_at_k": round(
                calculate_rate(full_gold_hit_count, normal_count),
                4,
            ),
            "p50_elapsed_ms": round(percentile(elapsed_values, 0.50), 2),
            "p95_elapsed_ms": round(percentile(elapsed_values, 0.95), 2),
        },
        "results": results,
    }


def compare_retrieval_modes(
        candidate_k: int = settings.default_candidate_k,
        sparse_limit: int = settings.default_sparse_limit,
        rerank_top_k: int = settings.default_rerank_top_k,
        cases: list[dict] | None = None,
        dataset_name: str = "legacy",
        rerank_document_diversity: bool = settings.rerank_document_diversity_enabled,
) -> dict:
    evaluations = [
        evaluate_retrieval_mode(
            mode,
            candidate_k,
            sparse_limit,
            rerank_top_k,
            cases,
            dataset_name,
            rerank_document_diversity,
        )
        for mode in RETRIEVAL_MODES
    ]
    return {
        "comparison": [
            {**evaluation["config"], **evaluation["metrics"]}
            for evaluation in evaluations
        ],
        "evaluations": evaluations,
    }


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description="Compare RAG retrieval branches.")
    parser.add_argument("--dataset", choices=DATASET_NAMES, default="legacy")
    parser.add_argument("--candidate-k", type=int, default=settings.default_candidate_k)
    parser.add_argument("--sparse-limit", type=int, default=settings.default_sparse_limit)
    parser.add_argument("--rerank-top-k", type=int, default=settings.default_rerank_top_k)
    parser.add_argument(
        "--rerank-document-diversity",
        action=argparse.BooleanOptionalAction,
        default=settings.rerank_document_diversity_enabled,
        help="Prefer one high-ranked Chunk per document before filling remaining slots.",
    )
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()
    evaluation_cases = load_evaluation_cases(args.dataset)
    comparison = compare_retrieval_modes(
        candidate_k=args.candidate_k,
        sparse_limit=args.sparse_limit,
        rerank_top_k=args.rerank_top_k,
        cases=evaluation_cases,
        dataset_name=args.dataset,
        rerank_document_diversity=args.rerank_document_diversity,
    )
    for summary in comparison["comparison"]:
        print(summary)
    saved_path = save_evaluation_result(
        comparison,
        "retrieval_ablation",
        result_type="comparison",
    )
    print(f"Saved to: {saved_path}")
