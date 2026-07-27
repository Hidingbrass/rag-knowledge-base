"""在同一冻结数据集和 RAG 配置上比较多个生成模型。

该脚本会调用真实模型并产生费用；默认只定义 qwen-plus，只有显式传入
``--models`` 才会比较更多模型。结果同时记录质量、拒答、Token、费用和 Rerank 延迟。
"""

from __future__ import annotations

import argparse

from app.core.config import settings
from app.evaluation.datasets import DATASET_NAMES, load_evaluation_cases
from app.evaluation.evaluate_generation_rerank import evaluate_generation_rerank
from app.evaluation.utils import calculate_rate, save_evaluation_result


QUALITY_METRICS = (
    "answer_pass_rate",
    "rejection_accuracy",
    "rejection_precision",
    "rejection_recall",
    "rejection_f1",
    "citation_valid_rate",
    "average_citation_support",
    "chunk_support_pass_rate",
    "fallback_rate",
    "average_rerank_elapsed_seconds",
)


def parse_models(value: str) -> list[str]:
    models = list(dict.fromkeys(
        item.strip()
        for item in value.split(",")
        if item.strip()
    ))
    if not models:
        raise argparse.ArgumentTypeError("models 不能为空")
    return models


def compare_generation_models(
        models: list[str],
        *,
        dataset_name: str = "technical_docs",
        repeat_count: int = 1,
        evaluator=evaluate_generation_rerank,
) -> dict:
    if repeat_count < 1:
        raise ValueError("repeat_count 必须大于等于 1")
    cases = load_evaluation_cases(dataset_name)
    comparisons = []

    for model in models:
        runs = [
            evaluator(
                candidate_k=settings.default_candidate_k,
                rerank_top_k=settings.default_rerank_top_k,
                rerank_min_score=settings.default_rerank_min_score,
                fallback_min_score=settings.default_fallback_min_score,
                retrieval_mode="hybrid",
                sparse_limit=settings.default_sparse_limit,
                dataset_name=dataset_name,
                cases=cases,
                generation_model=model,
            )
            for _ in range(repeat_count)
        ]
        metrics = {}
        for metric_name in QUALITY_METRICS:
            metrics[metric_name] = round(calculate_rate(
                sum(run["metrics"][metric_name] for run in runs),
                len(runs),
            ), 4)
        metrics["average_total_tokens"] = round(calculate_rate(
            sum(run["metrics"]["total_tokens"] for run in runs),
            len(runs),
        ))
        metrics["average_estimated_cost_yuan"] = round(calculate_rate(
            sum(run["metrics"]["estimated_cost_yuan"] for run in runs),
            len(runs),
        ), 6)
        comparisons.append({
            "model": model,
            "dataset": dataset_name,
            "repeat_count": repeat_count,
            **metrics,
        })

    return {
        "dataset": dataset_name,
        "case_count": len(cases),
        "repeat_count": repeat_count,
        "models": models,
        "comparison": comparisons,
    }


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Compare generation models on the same RAG dataset.",
    )
    parser.add_argument(
        "--models",
        type=parse_models,
        default=[settings.chat_model],
        help="Comma-separated model names.",
    )
    parser.add_argument(
        "--dataset",
        choices=DATASET_NAMES,
        default="technical_docs",
    )
    parser.add_argument("--repeat-count", type=int, default=1)
    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()
    result = compare_generation_models(
        args.models,
        dataset_name=args.dataset,
        repeat_count=args.repeat_count,
    )
    print(result["comparison"])
    path = save_evaluation_result(
        result,
        "generation_model_comparison",
        result_type="model_comparison",
    )
    print(f"Saved to: {path}")
