import argparse

from app.evaluation.cases import TEST_CASES
from app.evaluation.datasets import DATASET_NAMES, load_evaluation_cases
from app.core.config import settings
from app.evaluation.utils import (
    average_metric,
    calculate_rate,
    calculate_chunk_support_rate,
    evaluate_citations,
    is_rejection_answer,
    mark_recommended_rerank_min_score,
    parse_float_list,
    save_evaluation_result,
)
from app.schemas.rag import RerankRagChatRequest
from app.services.rag_service import rag_chat_with_rerank_response
from app.services.model_runtime import collect_model_usage, current_model_usage

RERANK_MIN_SCORE_VALUES = [0.72, 0.75, 0.78]


def evaluate_generation_rerank(
        candidate_k: int = settings.default_candidate_k,
        rerank_top_k: int = settings.default_rerank_top_k,
        rerank_min_score: float = settings.default_rerank_min_score,
        fallback_min_score: float = settings.default_fallback_min_score,
        slow_rerank_threshold: float = settings.slow_rerank_threshold,
        retrieval_mode: str = "vector",
        sparse_limit: int = settings.default_sparse_limit,
        dataset_name: str = "legacy",
        cases: list[dict] | None = None,
        generation_model: str | None = None,
):
    """评测回答关键词、拒答行为和引用编号是否合法。"""
    results = []

    # 普通题：统计答案是否命中全部预期关键词。
    normal_count = 0
    normal_passed = 0

    # 拒答题：统计知识库无答案时，系统是否正确拒绝回答。
    rejection_count = 0
    rejection_correct = 0

    # 引用：统计普通题是否包含引用，且编号都在 sources 范围内。
    citation_count = 0
    citation_valid_count = 0
    citation_support_sum = 0.0
    chunk_support_count = 0

    # 统计rerank调用次数
    rerank_mode_count = 0
    fallback_mode_count = 0
    unknown_mode_count = 0

    # 在 Rerank 生成评测中统计耗时指标
    rerank_elapsed_sum = 0.0
    rerank_elapsed_count = 0
    max_rerank_elapsed_seconds = 0.0

    slow_rerank_count = 0
    total_tokens = 0
    estimated_cost_yuan = 0.0
    rejection_true_positive = 0
    rejection_false_positive = 0
    rejection_false_negative = 0
    selected_cases = (
        cases
        if cases is not None
        else TEST_CASES if dataset_name == "legacy"
        else load_evaluation_cases(dataset_name)
    )

    for case in selected_cases:
        # 使用同一组检索参数运行完整 RAG 问答。
        request = RerankRagChatRequest(
            question=case["question"],
            candidate_k=candidate_k,
            rerank_top_k=rerank_top_k,
            rerank_min_score=rerank_min_score,
            fallback_min_score=fallback_min_score,
            retrieval_mode=retrieval_mode,
            sparse_limit=sparse_limit,
        )

        with collect_model_usage():
            response = (
                rag_chat_with_rerank_response(
                    request,
                    generation_model=generation_model,
                )
                if generation_model
                else rag_chat_with_rerank_response(request)
            )
            usage = current_model_usage()
        total_tokens += usage["total_tokens"]
        estimated_cost_yuan += usage["estimated_cost_yuan"]
        answer = response["answer"]

        sources = response["sources"]

        response_retrieval_mode = response.get("retrieval_mode")
        rerank_error = response.get("rerank_error")
        rerank_elapsed_seconds = response.get("rerank_elapsed_seconds")

        rounded_rerank_elapsed_seconds = (
            round(rerank_elapsed_seconds, 4)
            if rerank_elapsed_seconds is not None
            else None
        )

        if rerank_elapsed_seconds is not None:
            rerank_elapsed_count += 1
            rerank_elapsed_sum += rerank_elapsed_seconds
            if rerank_elapsed_seconds > max_rerank_elapsed_seconds:
                max_rerank_elapsed_seconds = rerank_elapsed_seconds
            if rerank_elapsed_seconds > slow_rerank_threshold:
                slow_rerank_count += 1

        if response_retrieval_mode == "rerank":
            rerank_mode_count += 1
        else:
            if response_retrieval_mode == "vector_fallback":
                fallback_mode_count += 1
            else:
                unknown_mode_count += 1

        citation_info = evaluate_citations(answer, sources)
        citation_numbers = citation_info["citation_numbers"]
        valid_citations = citation_info["valid_citations"]
        unique_valid_citations = citation_info["unique_valid_citations"]
        citations_valid = citation_info["citations_valid"]
        cited_sources = citation_info["cited_sources"]

        if case["should_reject"]:
            # 命中任意一个预设拒答短语，即认为本题正确拒答。
            rejection_passed = is_rejection_answer(answer)

            rejection_count += 1

            if rejection_passed:
                rejection_correct += 1
                rejection_true_positive += 1
            else:
                rejection_false_negative += 1

            results.append(
                {
                    "question": case["question"],
                    "answer": answer,
                    "type": "rejection",
                    "passed": rejection_passed,
                    "candidate_retrieval_mode": retrieval_mode,
                    "retrieval_mode": response_retrieval_mode,
                    "rerank_error": rerank_error,
                    "rerank_elapsed_seconds": rounded_rerank_elapsed_seconds,
                }
            )

            continue
        # 被引用的资料合并
        if is_rejection_answer(answer):
            rejection_false_positive += 1
        chunk_support = calculate_chunk_support_rate(
            case["expected_keywords"],
            cited_sources,
        )

        supported_keywords = chunk_support["supported_keywords"]
        missing_keywords = chunk_support["missing_keywords"]
        citation_support_rate = chunk_support["support_rate"]
        # 被大模型引用的关键字是否在资料的关键字中
        # 记录模型回答中实际出现的预期关键词。
        matched_keywords = [
            keyword
            for keyword in case["expected_keywords"]
            if keyword.lower() in answer.lower()
        ]

        # 关键词召回率 = 命中关键词数 / 预期关键词总数。
        keyword_recall = calculate_rate(
            len(matched_keywords),
            len(case["expected_keywords"]),
        )

        # 计算支持率 判断大模型是自己回答的还是依据资料回答的
        if citation_support_rate == 1.0:
            chunk_support_count += 1
        citation_support_sum += citation_support_rate

        normal_count += 1
        passed = keyword_recall == 1.0

        if passed:
            normal_passed += 1

        citation_count += 1

        if citations_valid:
            citation_valid_count += 1

        results.append(
            {
                "question": case["question"],
                "answer": answer,
                "expected_keywords": case["expected_keywords"],
                "matched_keywords": matched_keywords,
                "keyword_recall": round(keyword_recall, 4),
                "passed": passed,
                "citation_numbers": citation_numbers,
                "has_citation": len(citation_numbers) > 0,
                "source_count": len(sources),
                "valid_citations": valid_citations,
                "unique_valid_citations": unique_valid_citations,
                "citations_valid": citations_valid,
                "cited_sources": cited_sources,
                "supported_keywords": supported_keywords,
                "missing_keywords": missing_keywords,
                "citation_support_rate": round(citation_support_rate, 4),
                "rerank_error": rerank_error,
                "rerank_elapsed_seconds": rounded_rerank_elapsed_seconds,
                "candidate_retrieval_mode": retrieval_mode,
                "retrieval_mode": response_retrieval_mode,
                "sparse_limit": sparse_limit,
            }
        )

    # 汇总普通题、拒答题和引用格式三类指标。
    answer_pass_rate = calculate_rate(normal_passed, normal_count)

    rejection_accuracy = calculate_rate(rejection_correct, rejection_count)

    citation_valid_rate = calculate_rate(citation_valid_count, citation_count)

    average_citation_support = calculate_rate(citation_support_sum, citation_count)
    chunk_support_pass_rate = calculate_rate(chunk_support_count, citation_count)

    mode_total = rerank_mode_count + fallback_mode_count + unknown_mode_count

    fallback_rate = calculate_rate(fallback_mode_count, mode_total)

    slow_rerank_rate = calculate_rate(slow_rerank_count, rerank_elapsed_count)

    average_rerank_elapsed_seconds = calculate_rate(
        rerank_elapsed_sum,
        rerank_elapsed_count,
    )
    rejection_precision = calculate_rate(
        rejection_true_positive,
        rejection_true_positive + rejection_false_positive,
    )
    rejection_recall = calculate_rate(
        rejection_true_positive,
        rejection_true_positive + rejection_false_negative,
    )
    rejection_f1 = calculate_rate(
        2 * rejection_precision * rejection_recall,
        rejection_precision + rejection_recall,
    )

    return {
        "results": results,
        "metrics": {
            "answer_pass_rate": round(answer_pass_rate, 4),
            "normal_count": normal_count,
            "normal_passed": normal_passed,
            "rejection_accuracy": round(rejection_accuracy, 4),
            "rejection_count": rejection_count,
            "rejection_correct": rejection_correct,
            "citation_valid_rate": round(citation_valid_rate, 4),
            "citation_count": citation_count,
            "citation_valid_count": citation_valid_count,
            "average_citation_support": round(average_citation_support, 4),
            "chunk_support_pass_rate": round(chunk_support_pass_rate, 4),
            "chunk_support_count": chunk_support_count,
            "rerank_mode_count": rerank_mode_count,
            "fallback_mode_count": fallback_mode_count,
            "unknown_mode_count": unknown_mode_count,
            "fallback_rate": round(fallback_rate, 4),
            "average_rerank_elapsed_seconds": round(average_rerank_elapsed_seconds, 4),
            "max_rerank_elapsed_seconds": round(max_rerank_elapsed_seconds, 4),
            "rerank_elapsed_count": rerank_elapsed_count,
            "slow_rerank_count": slow_rerank_count,
            "slow_rerank_rate": round(slow_rerank_rate, 4),
            "rejection_precision": round(rejection_precision, 4),
            "rejection_recall": round(rejection_recall, 4),
            "rejection_f1": round(rejection_f1, 4),
            "total_tokens": total_tokens,
            "estimated_cost_yuan": round(estimated_cost_yuan, 6),
        },
        "config": {
            "dataset": dataset_name,
            "generation_model": generation_model or settings.chat_model,
            "candidate_k": candidate_k,
            "rerank_top_k": rerank_top_k,
            "rerank_min_score": rerank_min_score,
            "fallback_min_score": fallback_min_score,
            "slow_rerank_threshold": slow_rerank_threshold,
            "retrieval_mode": retrieval_mode,
            "sparse_limit": sparse_limit,
        }
    }


def compare_rerank_min_scores(
        candidate_k=settings.default_candidate_k,
        rerank_top_k=settings.default_rerank_top_k,
        repeat_count=3,
        scores=None,
):
    if scores is None:
        scores = RERANK_MIN_SCORE_VALUES

    comparison_results = []

    for score in scores:
        run_results = []
        for _ in range(repeat_count):
            result = evaluate_generation_rerank(
                candidate_k=candidate_k,
                rerank_top_k=rerank_top_k,
                rerank_min_score=score,
            )
            metrics = result["metrics"]
            run_results.append(metrics)

        comparison_results.append({
            "rerank_min_score": score,
            "candidate_k": candidate_k,
            "rerank_top_k": rerank_top_k,
            "answer_pass_rate": round(average_metric(run_results, "answer_pass_rate"), 4),
            "rejection_accuracy": round(average_metric(run_results, "rejection_accuracy"), 4),
            "citation_valid_rate": round(average_metric(run_results, "citation_valid_rate"), 4),
            "average_citation_support": round(average_metric(run_results, "average_citation_support"), 4),
            "chunk_support_pass_rate": round(average_metric(run_results, "chunk_support_pass_rate"), 4),
            "repeat_count": repeat_count,
        })

    return mark_recommended_rerank_min_score(comparison_results)


def run_single_rerank_generation_evaluation(
        candidate_k,
        rerank_top_k,
        rerank_min_score,
        retrieval_mode,
        sparse_limit,
):
    evaluation = evaluate_generation_rerank(
        candidate_k=candidate_k,
        rerank_top_k=rerank_top_k,
        rerank_min_score=rerank_min_score,
        retrieval_mode=retrieval_mode,
        sparse_limit=sparse_limit,
    )

    print(evaluation["metrics"])

    saved_path = save_evaluation_result(
        evaluation,
        "rerank_generation",
        result_type="single_evaluation",
    )

    print(f"Saved to: {saved_path}")

    return evaluation


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Run Rerank generation evaluation.",
    )

    parser.add_argument(
        "--dataset",
        choices=DATASET_NAMES,
        default="legacy",
    )

    parser.add_argument(
        "--generation-model",
        default=settings.chat_model,
        help="DashScope OpenAI-compatible generation model.",
    )

    parser.add_argument(
        "--mode",
        choices=["single", "comparison"],
        default="comparison",
        help="Evaluation mode to run.",
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
        help="Number of reranked chunks kept for generation.",
    )

    parser.add_argument(
        "--rerank-min-score",
        type=float,
        default=settings.default_rerank_min_score,
        help="Minimum top rerank score for answering in single mode.",
    )

    parser.add_argument(
        "--repeat-count",
        type=int,
        default=3,
        help="Repeat count for comparison mode.",
    )

    parser.add_argument(
        "--rerank-min-score-values",
        type=parse_float_list,
        default=RERANK_MIN_SCORE_VALUES,
        help="Comma-separated rerank_min_score values for comparison mode.",
    )

    parser.add_argument(
        "--retrieval-mode",
        choices=["vector", "hybrid"],
        default="vector",
    )

    parser.add_argument(
        "--sparse-limit", "--keyword-limit",
        dest="sparse_limit",
        type=int,
        default=settings.default_sparse_limit,
    )

    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()

    if args.mode == "single":
        run_single_rerank_generation_evaluation(
            candidate_k=args.candidate_k,
            rerank_top_k=args.rerank_top_k,
            rerank_min_score=args.rerank_min_score,
            retrieval_mode=args.retrieval_mode,
            sparse_limit=args.sparse_limit,
            dataset_name=args.dataset,
            generation_model=args.generation_model,
        )

    elif args.mode == "comparison":
        comparison_results = compare_rerank_min_scores(
            candidate_k=args.candidate_k,
            rerank_top_k=args.rerank_top_k,
            repeat_count=args.repeat_count,
            scores=args.rerank_min_score_values,
        )

        for item in comparison_results:
            print(item)

        saved_path = save_evaluation_result(
            comparison_results,
            "rerank_min_score_comparison",
            result_type="comparison",
        )

        print(f"Saved to: {saved_path}")
