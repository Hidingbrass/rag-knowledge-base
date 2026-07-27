"""Evaluation dataset loading and validation."""

from __future__ import annotations

import json
from pathlib import Path

from app.evaluation.cases import TEST_CASES


DATASET_NAMES = ("legacy", "technical_docs")
TECHNICAL_DOCS_DATASET_PATH = (
    Path(__file__).resolve().parents[2]
    / "demo"
    / "technical_docs"
    / "evaluation_cases.json"
)


def _validate_technical_case(case: dict) -> None:
    required_fields = {
        "id",
        "category",
        "question",
        "gold_documents",
        "expected_keywords",
        "should_reject",
    }
    missing_fields = required_fields - case.keys()
    if missing_fields:
        raise ValueError(
            f"technical_docs case missing fields: {', '.join(sorted(missing_fields))}"
        )
    if not case["id"] or not case["question"]:
        raise ValueError("technical_docs case id and question must not be empty")
    if not isinstance(case["gold_documents"], list):
        raise ValueError("gold_documents must be a list")
    if not isinstance(case["expected_keywords"], list):
        raise ValueError("expected_keywords must be a list")
    if case["should_reject"]:
        if case["gold_documents"] or case["expected_keywords"]:
            raise ValueError("rejection cases must not define gold documents or keywords")
    elif not case["gold_documents"] or not case["expected_keywords"]:
        raise ValueError("answerable cases require gold documents and expected keywords")


def load_technical_docs_dataset(path: Path = TECHNICAL_DOCS_DATASET_PATH) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    cases = payload.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("technical_docs dataset must contain a non-empty cases list")

    seen_ids = set()
    for case in cases:
        _validate_technical_case(case)
        if case["id"] in seen_ids:
            raise ValueError(f"duplicate technical_docs case id: {case['id']}")
        seen_ids.add(case["id"])
    return cases


def load_evaluation_cases(dataset_name: str) -> list[dict]:
    if dataset_name == "legacy":
        return TEST_CASES
    if dataset_name == "technical_docs":
        return load_technical_docs_dataset()
    raise ValueError(f"dataset must be one of: {', '.join(DATASET_NAMES)}")
