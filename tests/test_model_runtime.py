from types import SimpleNamespace
from contextvars import Context

import pytest

from app.services import model_runtime


@pytest.fixture(autouse=True)
def reset_breakers():
    model_runtime.reset_circuit_breakers()
    yield
    model_runtime.reset_circuit_breakers()


def runtime_settings(**overrides):
    values = {
        "model_retry_max_attempts": 3,
        "model_retry_backoff_seconds": 0.0,
        "model_circuit_failure_threshold": 2,
        "model_circuit_reset_seconds": 30.0,
        "chat_input_price_per_million_yuan": 1.0,
        "chat_output_price_per_million_yuan": 2.0,
        "embedding_price_per_million_yuan": 0.5,
        "rerank_price_per_million_yuan": 0.25,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


def test_transient_error_retries_then_succeeds(monkeypatch):
    monkeypatch.setattr(model_runtime, "settings", runtime_settings())
    calls = []

    def flaky_call():
        calls.append(1)
        if len(calls) < 3:
            raise TimeoutError("temporary")
        return "ok"

    result, attempts, elapsed_ms = model_runtime.execute_with_resilience(
        flaky_call,
        circuit_key="chat:test",
        is_retryable=lambda error: isinstance(error, TimeoutError),
    )

    assert result == "ok"
    assert attempts == 3
    assert elapsed_ms >= 0


def test_non_retryable_failures_open_circuit(monkeypatch):
    monkeypatch.setattr(model_runtime, "settings", runtime_settings())

    for _ in range(2):
        with pytest.raises(ValueError):
            model_runtime.execute_with_resilience(
                lambda: (_ for _ in ()).throw(ValueError("bad request")),
                circuit_key="chat:test",
                is_retryable=lambda error: False,
            )

    with pytest.raises(model_runtime.ModelCircuitOpenError):
        model_runtime.execute_with_resilience(
            lambda: "not called",
            circuit_key="chat:test",
            is_retryable=lambda error: False,
        )


def test_request_scope_aggregates_usage_and_cost(monkeypatch):
    monkeypatch.setattr(model_runtime, "settings", runtime_settings())

    with model_runtime.collect_model_usage():
        model_runtime.record_model_call(model_runtime.ModelCallRecord(
            model="model-a",
            operation="chat",
            success=True,
            attempts=2,
            elapsed_ms=100,
            prompt_tokens=100,
            completion_tokens=50,
            total_tokens=150,
            estimated_cost_yuan=model_runtime.estimate_chat_cost(100, 50),
        ))
        usage = model_runtime.current_model_usage()

    assert usage["models"] == ["model-a"]
    assert usage["retry_count"] == 1
    assert usage["total_tokens"] == 150
    assert usage["estimated_cost_yuan"] == 0.0002


def test_explicit_collector_can_be_rebound_in_different_contexts():
    records = []

    def append_record(model):
        with model_runtime.collect_model_usage(records):
            model_runtime.record_model_call(model_runtime.ModelCallRecord(
                model=model,
                operation="chat",
                success=True,
                attempts=1,
                elapsed_ms=1,
            ))

    Context().run(append_record, "model-a")
    Context().run(append_record, "model-b")

    usage = model_runtime.summarize_model_usage(records)
    assert usage["models"] == ["model-a", "model-b"]
    assert usage["upstream_call_count"] == 2
