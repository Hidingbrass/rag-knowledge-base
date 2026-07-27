"""模型调用的请求级用量收集、受控重试和轻量熔断。"""

from __future__ import annotations

import time
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import asdict, dataclass
from threading import Lock
from typing import Callable, TypeVar

from app.core.config import settings


T = TypeVar("T")


@dataclass
class ModelCallRecord:
    model: str
    operation: str
    success: bool
    attempts: int
    elapsed_ms: int
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0
    estimated_cost_yuan: float = 0.0


_request_records: ContextVar[list[ModelCallRecord] | None] = ContextVar(
    "model_call_records",
    default=None,
)


@contextmanager
def collect_model_usage(records: list[ModelCallRecord] | None = None):
    """在当前执行上下文绑定用量收集器。

    普通请求不传 ``records``，由这里创建请求级列表。同步生成器被
    ``StreamingResponse`` 放入线程池后，每次 ``next()`` 可能运行在不同
    Context；这种情况下由路由持有同一个显式列表，并在每次迭代时重新绑定，
    避免跨 Context 重置 Token。
    """
    active_records = records if records is not None else []
    token = _request_records.set(active_records)
    try:
        yield active_records
    finally:
        _request_records.reset(token)


def record_model_call(record: ModelCallRecord) -> None:
    records = _request_records.get()
    if records is not None:
        records.append(record)


def summarize_model_usage(records: list[ModelCallRecord] | None) -> dict:
    records = records or []
    return {
        "models": sorted({record.model for record in records}),
        "upstream_call_count": len(records),
        "retry_count": sum(max(0, record.attempts - 1) for record in records),
        "prompt_tokens": sum(record.prompt_tokens for record in records),
        "completion_tokens": sum(record.completion_tokens for record in records),
        "total_tokens": sum(record.total_tokens for record in records),
        "estimated_cost_yuan": round(
            sum(record.estimated_cost_yuan for record in records),
            6,
        ),
    }


def current_model_usage() -> dict:
    return summarize_model_usage(_request_records.get())


def current_model_call_records() -> list[dict]:
    return [asdict(record) for record in (_request_records.get() or [])]


def estimate_chat_cost(prompt_tokens: int, completion_tokens: int) -> float:
    return (
        prompt_tokens * settings.chat_input_price_per_million_yuan
        + completion_tokens * settings.chat_output_price_per_million_yuan
    ) / 1_000_000


def estimate_embedding_cost(total_tokens: int) -> float:
    return total_tokens * settings.embedding_price_per_million_yuan / 1_000_000


def estimate_rerank_cost(total_tokens: int) -> float:
    return total_tokens * settings.rerank_price_per_million_yuan / 1_000_000


class ModelCircuitOpenError(RuntimeError):
    pass


@dataclass
class _CircuitState:
    consecutive_failures: int = 0
    opened_at: float | None = None


_circuit_states: dict[str, _CircuitState] = {}
_circuit_lock = Lock()


def reset_circuit_breakers() -> None:
    """供测试和运维探针在明确需要时重置进程内熔断状态。"""
    with _circuit_lock:
        _circuit_states.clear()


def _before_call(circuit_key: str) -> None:
    with _circuit_lock:
        state = _circuit_states.setdefault(circuit_key, _CircuitState())
        if state.opened_at is None:
            return
        if time.monotonic() - state.opened_at >= settings.model_circuit_reset_seconds:
            state.opened_at = None
            state.consecutive_failures = 0
            return
        raise ModelCircuitOpenError(f"模型服务熔断中: {circuit_key}")


def _record_success(circuit_key: str) -> None:
    with _circuit_lock:
        _circuit_states[circuit_key] = _CircuitState()


def _record_failure(circuit_key: str) -> None:
    with _circuit_lock:
        state = _circuit_states.setdefault(circuit_key, _CircuitState())
        state.consecutive_failures += 1
        if state.consecutive_failures >= settings.model_circuit_failure_threshold:
            state.opened_at = time.monotonic()


def record_circuit_failure(circuit_key: str) -> None:
    _record_failure(circuit_key)


def execute_with_resilience(
    call: Callable[[], T],
    *,
    circuit_key: str,
    is_retryable: Callable[[Exception], bool],
) -> tuple[T, int, int]:
    """执行模型调用；只重试明确的瞬时错误，并返回尝试次数和总耗时。"""
    _before_call(circuit_key)
    started_at = time.perf_counter()
    max_attempts = max(1, settings.model_retry_max_attempts)
    attempts = 0

    while True:
        attempts += 1
        try:
            result = call()
            _record_success(circuit_key)
            elapsed_ms = int((time.perf_counter() - started_at) * 1000)
            return result, attempts, max(0, elapsed_ms)
        except Exception as error:
            if attempts >= max_attempts or not is_retryable(error):
                _record_failure(circuit_key)
                try:
                    setattr(error, "_model_attempts", attempts)
                    setattr(
                        error,
                        "_model_elapsed_ms",
                        max(0, int((time.perf_counter() - started_at) * 1000)),
                    )
                except Exception:
                    pass
                raise
            backoff = settings.model_retry_backoff_seconds * (2 ** (attempts - 1))
            time.sleep(max(0.0, backoff))
