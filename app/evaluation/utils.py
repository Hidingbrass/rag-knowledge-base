import json
import re
from datetime import datetime
from pathlib import Path

REJECTION_PHRASES = [
    "没有足够相关的资料",
    "资料不足",
    "无法基于资料回答",
    "没有检索到",
    "没有提及",
    "未提及",
    "未说明",
    "无法确定",
    "无法判断",
]


def is_rejection_answer(answer):
    return any(
        phrase in answer
        for phrase in REJECTION_PHRASES
    )


def average_metric(metrics_list, metric_name):
    if not metrics_list:
        return 0.0

    total = 0.0

    for metrics in metrics_list:
        total += metrics[metric_name]

    return total / len(metrics_list)


def calculate_rate(numerator, denominator):
    if not denominator:
        return 0.0

    return numerator / denominator


def parse_int_list(value):
    return [
        int(item.strip())
        for item in value.split(",")
        if item.strip()
    ]


def parse_float_list(value):
    return [
        float(item.strip())
        for item in value.split(",")
        if item.strip()
    ]


def mark_recommended_rerank_config(summaries):
    qualified_summaries = []
    for summary in summaries:
        if (summary["answer_pass_rate"] == 1.0
                and summary["rejection_accuracy"] == 1.0
                and summary["citation_valid_rate"] == 1.0
                and summary["average_citation_support"] == 1.0
                and summary["chunk_support_pass_rate"] == 1.0
                and summary["fallback_rate"] == 0.0):
            qualified_summaries.append(summary)

    recommended_summary = None

    if qualified_summaries:
        recommended_summary = min(
            qualified_summaries,
            key=lambda summary: (
                summary["slow_rerank_rate"],
                summary["rerank_top_k"],
                summary["candidate_k"],
                summary["average_rerank_elapsed_seconds"],
            ),
        )

    for summary in summaries:
        summary["is_recommended"] = summary is recommended_summary

    return summaries


def mark_recommended_rerank_min_score(summaries):
    qualified_summaries = []
    for summary in summaries:
        if (summary["answer_pass_rate"] == 1.0
                and summary["rejection_accuracy"] == 1.0
                and summary["citation_valid_rate"] == 1.0
                and summary["average_citation_support"] == 1.0
                and summary["chunk_support_pass_rate"] == 1.0):
            qualified_summaries.append(summary)

    recommended_summary = None

    if qualified_summaries:
        recommended_summary = min(
            qualified_summaries,
            key=lambda summary: summary["rerank_min_score"],
        )

    for summary in summaries:
        summary["is_recommended"] = summary is recommended_summary

    return summaries


def mark_recommended_rerank_retrieval_mode(summaries):
    recommended_summary = None

    if summaries:
        recommended_summary = max(
            summaries,
            key=lambda summary: (
                summary["rerank_rejection_accuracy"],
                summary["rerank_hit_rate"],
                summary["candidate_hit_rate"],
                1 if summary["retrieval_mode"] == "vector" else 0,
            ),
        )

    for summary in summaries:
        summary["is_recommended"] = summary is recommended_summary

    return summaries


def save_evaluation_result(
        result,
        filename_prefix,
        result_type="evaluation",
        output_dir="evaluation_results",
):
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    now = datetime.now()
    timestamp = now.strftime("%Y%m%d_%H%M%S")
    created_at = now.strftime("%Y-%m-%d %H:%M:%S")

    filename = f"{filename_prefix}_{timestamp}.json"

    output_path = output_dir / filename

    payload = {
        "experiment_name": filename_prefix,
        "result_type": result_type,
        "created_at": created_at,
        "results": result,
    }

    with output_path.open("w", encoding="utf-8") as file:
        json.dump(payload, file, ensure_ascii=False, indent=2)

    return output_path


def calculate_chunk_support_rate(expected_keywords, cited_sources):
    cited_text = " ".join(
        source.get("text", "")
        for source in cited_sources
    )

    supported_keywords = [
        keyword
        for keyword in expected_keywords
        if keyword.lower() in cited_text.lower()
    ]

    support_rate = calculate_rate(
        len(supported_keywords),
        len(expected_keywords),
    )

    missing_keywords = [
        keyword
        for keyword in expected_keywords
        if keyword.lower() not in cited_text.lower()
    ]

    results = {
        "supported_keywords": supported_keywords,
        "missing_keywords": missing_keywords,
        "support_rate": support_rate,
    }

    return results


def evaluate_citations(answer, sources):
    citation_numbers = [
        int(number)
        for number in re.findall(r"\[(\d+)]", answer)
    ]

    valid_citations = [
        number
        for number in citation_numbers
        if 1 <= number <= len(sources)
    ]

    unique_valid_citations = []
    for number in valid_citations:
        if number not in unique_valid_citations:
            unique_valid_citations.append(number)

    citations_valid = (
            len(citation_numbers) > 0
            and len(valid_citations) == len(citation_numbers)
    )

    cited_sources = [
        sources[number - 1]
        for number in unique_valid_citations
    ]

    return {
        "citation_numbers": citation_numbers,
        "valid_citations": valid_citations,
        "unique_valid_citations": unique_valid_citations,
        "citations_valid": citations_valid,
        "cited_sources": cited_sources,
    }
