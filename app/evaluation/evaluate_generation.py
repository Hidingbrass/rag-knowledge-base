import argparse

from app.evaluation.cases import TEST_CASES
from app.core.config import settings
from app.evaluation.utils import (
    calculate_rate,
    calculate_chunk_support_rate,
    evaluate_citations,
    is_rejection_answer,
    save_evaluation_result,
)
from app.schemas.rag import RagChatRequest
from app.services.rag_service import rag_chat_response


def evaluate_generation(
        top_k: int = settings.default_top_k,
        min_score: float = settings.default_min_score,
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

    for case in TEST_CASES:
        # 使用同一组检索参数运行完整 RAG 问答。
        request = RagChatRequest(
            question=case["question"],
            top_k=top_k,
            min_score=min_score,
        )

        response = rag_chat_response(request)
        answer = response["answer"]

        sources = response["sources"]

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

            results.append(
                {
                    "question": case["question"],
                    "answer": answer,
                    "type": "rejection",
                    "passed": rejection_passed,
                }
            )

            continue
        # 被引用的资料合并
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
            }
        )

    # 汇总普通题、拒答题和引用格式三类指标。
    answer_pass_rate = calculate_rate(normal_passed, normal_count)

    rejection_accuracy = calculate_rate(rejection_correct, rejection_count)

    citation_valid_rate = calculate_rate(citation_valid_count, citation_count)

    average_citation_support = calculate_rate(citation_support_sum, citation_count)

    chunk_support_pass_rate = calculate_rate(chunk_support_count, citation_count)

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
        },
    }


def run_single_generation_evaluation(top_k, min_score):
    evaluation = evaluate_generation(
        top_k=top_k,
        min_score=min_score,
    )
    print(evaluation["metrics"])
    saved_path = save_evaluation_result(
        evaluation,
        "generation",
        result_type="single_evaluation",
    )
    print(f"Saved to: {saved_path}")

    return evaluation


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="Run generation evaluation.",
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
        help="Minimum vector score for sources.",
    )

    return parser.parse_args(argv)


if __name__ == "__main__":
    args = parse_args()

    run_single_generation_evaluation(
        top_k=args.top_k,
        min_score=args.min_score,
    )
