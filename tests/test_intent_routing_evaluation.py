import json

from app.evaluation.evaluate_intent_routing import (
    DEFAULT_INTENT_DATASET,
    evaluate_intent_routing,
    parse_args,
)


def test_intent_routing_dataset_has_balanced_safety_cases():
    cases = json.loads(DEFAULT_INTENT_DATASET.read_text(encoding="utf-8"))["cases"]

    assert len(cases) >= 20
    assert {case["expected"]["intent"] for case in cases} == {
        "KNOWLEDGE_QA",
        "OPEN_DOMAIN_CHAT",
        "TOOL_CALL",
        "CLARIFICATION",
    }
    assert any(case["safety_critical"] for case in cases)
    assert any(case["expected"]["operation"] == "WRITE_TOOL" for case in cases)
    assert any(case["expected"]["freshness"] == "REALTIME" for case in cases)


def test_evaluator_reports_perfect_metrics_for_expected_predictions():
    cases = json.loads(DEFAULT_INTENT_DATASET.read_text(encoding="utf-8"))["cases"]
    expected_by_question = {
        case["question"]: case["expected"]
        for case in cases
    }

    def perfect_classifier(request):
        return expected_by_question[request.question]

    report = evaluate_intent_routing(classifier=perfect_classifier)

    assert report["case_count"] == len(cases)
    assert report["pass_rate"] == 1.0
    assert report["safety_critical_pass_rate"] == 1.0
    assert report["enterprise_scope_recall"] == 1.0
    assert report["write_tool_recall"] == 1.0
    assert report["realtime_recall"] == 1.0


def test_evaluator_keeps_running_and_penalizes_classifier_errors():
    def failing_classifier(request):
        if "合同" in request.question:
            raise TimeoutError("classifier timeout")
        return {
            "intent": "OPEN_DOMAIN_CHAT",
            "knowledge_scope": "PUBLIC",
            "operation": "ANSWER",
            "freshness": "STATIC",
        }

    report = evaluate_intent_routing(classifier=failing_classifier)

    assert report["pass_rate"] < 1.0
    assert report["enterprise_scope_recall"] < 1.0
    assert report["intent_confusion_matrix"]["KNOWLEDGE_QA"]["ERROR"] >= 1


def test_intent_routing_parse_args_uses_default_dataset():
    args = parse_args([])

    assert args.dataset == DEFAULT_INTENT_DATASET
    assert args.output is None
