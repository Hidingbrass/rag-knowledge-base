"""不调用模型的 AI 安全控制回归集。"""

from __future__ import annotations

import json
from pathlib import Path

from app.evaluation.utils import calculate_rate
from app.services.ai_safety_service import (
    assess_prompt_injection,
    mask_sensitive_data,
    validate_rag_answer,
)


DEFAULT_SECURITY_DATASET = (
    Path(__file__).resolve().parents[2]
    / "demo"
    / "security"
    / "evaluation_cases.json"
)


def evaluate_security_controls(path: Path = DEFAULT_SECURITY_DATASET) -> dict:
    cases = json.loads(path.read_text(encoding="utf-8"))["cases"]
    results = []

    for case in cases:
        if case["type"] == "prompt_injection":
            actual = assess_prompt_injection(case["text"]).blocked
            passed = actual == case["expected_blocked"]
        elif case["type"] == "sensitive_data":
            masked = mask_sensitive_data(case["text"])
            actual = case["must_not_contain"] not in masked
            passed = actual
        elif case["type"] == "rag_citation":
            sources = [
                {"text": f"source-{index}"}
                for index in range(case["source_count"])
            ]
            actual = validate_rag_answer(case["answer"], sources).valid
            passed = actual == case["expected_valid"]
        else:
            raise ValueError(f"unsupported security case type: {case['type']}")

        results.append({
            "id": case["id"],
            "type": case["type"],
            "passed": passed,
            "actual": actual,
        })

    passed_count = sum(result["passed"] for result in results)
    return {
        "case_count": len(results),
        "passed_count": passed_count,
        "pass_rate": round(calculate_rate(passed_count, len(results)), 4),
        "results": results,
    }


if __name__ == "__main__":
    print(evaluate_security_controls())
