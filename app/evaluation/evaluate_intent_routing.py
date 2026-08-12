"""调用真实或替代分类器评测意图路由结构化输出。"""

from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from collections import defaultdict
from pathlib import Path
from typing import Callable

from app.evaluation.utils import calculate_rate
from app.schemas.chat import IntentClassificationRequest
from app.services.intent_service import classify_intent
from app.core.config import settings


DEFAULT_INTENT_DATASET = (
    Path(__file__).resolve().parents[2]
    / "demo"
    / "intent_routing"
    / "evaluation_cases.json"
)
PROJECT_ROOT = Path(__file__).resolve().parents[2]

Classifier = Callable[[IntentClassificationRequest], dict]
EVALUATED_FIELDS = ("intent", "knowledge_scope", "operation", "freshness")
DEFAULT_THRESHOLD_CANDIDATES = (0.50, 0.60, 0.70, 0.80, 0.85, 0.90, 0.95)


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

    threshold_calibration = _calibrate_thresholds(results)
    return {
        "evaluated_at": datetime.now(timezone.utc).isoformat(),
        "model": settings.intent_model,
        "configured_threshold": settings.intent_classifier_min_confidence,
        "dataset": _display_path(path),
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
        "threshold_calibration": threshold_calibration,
        "results": results,
    }


def _rate(results: list[dict], predicate: Callable[[dict], bool]) -> float:
    return round(
        calculate_rate(sum(predicate(result) for result in results), len(results)),
        4,
    )


def _display_path(path: Path) -> str:
    """优先输出仓库相对路径，避免评测产物绑定本机目录。"""
    resolved = path.resolve()
    try:
        return str(resolved.relative_to(PROJECT_ROOT))
    except ValueError:
        return str(resolved)


def _calibrate_thresholds(results: list[dict]) -> dict:
    """按生产降级规则扫描阈值；低置信或企业标记都视为 RAG。"""
    candidates = []
    for threshold in DEFAULT_THRESHOLD_CANDIDATES:
        evaluated = []
        for result in results:
            actual = result["actual"]
            expected_intent = result["expected"]["intent"]
            if actual is None:
                effective_intent = "ERROR"
            else:
                confidence = float(actual.get("confidence", 1.0))
                enterprise = bool(actual.get("enterprise_knowledge")) \
                    or actual.get("knowledge_scope") == "ENTERPRISE"
                effective_intent = (
                    "KNOWLEDGE_QA"
                    if enterprise or confidence < threshold
                    else str(actual.get("intent", "ERROR"))
                )
            evaluated.append((expected_intent, effective_intent))

        enterprise = [item for item in evaluated if item[0] == "KNOWLEDGE_QA"]
        expected_non_rag = [item for item in evaluated if item[0] != "KNOWLEDGE_QA"]
        candidates.append({
            "threshold": threshold,
            "effective_intent_accuracy": round(calculate_rate(
                sum(expected == actual for expected, actual in evaluated),
                len(evaluated),
            ), 4),
            "enterprise_rag_recall": round(calculate_rate(
                sum(actual == "KNOWLEDGE_QA" for _, actual in enterprise),
                len(enterprise),
            ), 4),
            "non_rag_coverage": round(calculate_rate(
                sum(actual != "KNOWLEDGE_QA" for _, actual in expected_non_rag),
                len(expected_non_rag),
            ), 4),
        })

    safe_candidates = [
        item for item in candidates if item["enterprise_rag_recall"] == 1.0
    ]
    best_accuracy = max(
        (item["effective_intent_accuracy"] for item in safe_candidates),
        default=0.0,
    )
    recommended = max(
        (
            item for item in safe_candidates
            if item["effective_intent_accuracy"] == best_accuracy
        ),
        key=lambda item: item["threshold"],
        default=None,
    )
    return {
        "selection_rule": "企业知识 RAG 召回率为 1.0 时，选择意图准确率最高者；并列取更保守的较高阈值",
        "recommended_threshold": None if recommended is None else recommended["threshold"],
        "candidates": candidates,
        "limitation": "基于小规模人工集和模型自报 confidence，仅用于本项目初始阈值，不代表概率校准",
    }


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
