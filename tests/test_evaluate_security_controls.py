from app.evaluation.evaluate_security_controls import evaluate_security_controls


def test_security_control_dataset_passes_without_model_calls():
    result = evaluate_security_controls()

    assert result["case_count"] == 10
    assert result["passed_count"] == 10
    assert result["pass_rate"] == 1.0
