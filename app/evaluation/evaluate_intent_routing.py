"""调用真实或替代分类器评测意图路由结构化输出。"""

from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Callable

from app.evaluation.utils import calculate_rate
from app.schemas.chat import IntentClassificationRequest
from app.services.intent_service import classify_intent


DEFAULT_INTENT_DATASET = (
    Path(__file__).resolve().parents[2]
    / "demo"
    / "intent_routing"
    / "evaluation_cases.json"
)

Classifier = Callable[[IntentClassificationRequest], dict]
EVALUATED_FIELDS = ("intent", "knowledge_scope", "operation", "freshness")


def evaluate_intent_routing(
        path: Path = DEFAULT_INTENT_DATASET,
        classifier: Classifier = classify_intent,
) -> dict:
    """运行标注集并输出字段准确率、安全召回率和意图混淆矩阵。"""
    cases = json.loads(path.read_text(encoding="utf-8"))["cases"]
    results = []
    field_passes = defaultdict(int)
    confusion_matrix: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))

    for case in cases:
        expected = case["expected"]
        try:
            actual = classifier(IntentClassificationRequest(question=case["question"]))
            field_results = {
                field: actual.get(field) == expected[field]
                for field in EVALUATED_FIELDS
            }
            for field, passed in field_results.items():
                field_passes[field] += int(passed)
            passed = all(field_results.values())
            error = None
            predicted_intent = str(actual.get("intent", "ERROR"))
        except Exception as exception:  # 评测需要记录单例失败并继续完成全集。
            actual = None
            field_results = {field: False for field in EVALUATED_FIELDS}
            passed = False
            error = str(exception)
            predicted_intent = "ERROR"

        confusion_matrix[expected["intent"]][predicted_intent] += 1
        results.append({
            "id": case["id"],
            "question": case["question"],
            "safety_critical": case["safety_critical"],
            "expected": expected,
            "actual": actual,
            "field_results": field_results,
            "passed": passed,
            "error": error,
        })

    case_count = len(results)
    safety_results = [result for result in results if result["safety_critical"]]
    enterprise_results = [
        result
        for result in results
        if result["expected"]["knowledge_scope"] == "ENTERPRISE"
    ]
    write_results = [
        result
        for result in results
        if result["expected"]["operation"] == "WRITE_TOOL"
    ]
    realtime_results = [
        result
        for result in results
        if result["expected"]["freshness"] == "REALTIME"
    ]

    return {
        "dataset": str(path),
        "case_count": case_count,
        "passed_count": sum(result["passed"] for result in results),
        "pass_rate": _rate(results, lambda result: result["passed"]),
        "field_accuracy": {
            field: round(calculate_rate(field_passes[field], case_count), 4)
            for field in EVALUATED_FIELDS
        },
        "safety_critical_pass_rate": _rate(
            safety_results,
            lambda result: result["passed"],
        ),
        "enterprise_scope_recall": _rate(
            enterprise_results,
            lambda result: (
                result["actual"] is not None
                and result["actual"].get("knowledge_scope") == "ENTERPRISE"
            ),
        ),
        "write_tool_recall": _rate(
            write_results,
            lambda result: (
                result["actual"] is not None
                and result["actual"].get("operation") == "WRITE_TOOL"
            ),
        ),
        "realtime_recall": _rate(
            realtime_results,
            lambda result: (
                result["actual"] is not None
                and result["actual"].get("freshness") == "REALTIME"
            ),
        ),
        "intent_confusion_matrix": {
            expected: dict(predictions)
            for expected, predictions in confusion_matrix.items()
        },
        "results": results,
    }


def _rate(results: list[dict], predicate: Callable[[dict], bool]) -> float:
    return round(
        calculate_rate(sum(predicate(result) for result in results), len(results)),
        4,
    )


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="评测意图分类与高风险路由字段")
    parser.add_argument("--dataset", type=Path, default=DEFAULT_INTENT_DATASET)
    parser.add_argument("--output", type=Path)
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> dict:
    args = parse_args(argv)
    report = evaluate_intent_routing(args.dataset)
    rendered = json.dumps(report, ensure_ascii=False, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    else:
        print(rendered)
    return report


if __name__ == "__main__":
    main()
