import json
from contextvars import Context

import pytest

from app.api import rag as rag_api
from app.schemas.rag import RerankRagChatRequest
from app.services.model_runtime import ModelCallRecord, record_model_call


def test_stream_event_lines_keeps_usage_across_different_contexts(monkeypatch):
    def fake_events(request):
        record_model_call(ModelCallRecord(
            model="embedding-test",
            operation="embedding",
            success=True,
            attempts=1,
            elapsed_ms=1,
            total_tokens=10,
        ))
        yield {"type": "status", "stage": "retrieving"}
        record_model_call(ModelCallRecord(
            model="chat-test",
            operation="chat_completion_stream",
            success=True,
            attempts=1,
            elapsed_ms=2,
            total_tokens=20,
        ))
        yield {"type": "done"}

    monkeypatch.setattr(
        rag_api,
        "rag_chat_with_rerank_stream_events",
        fake_events,
    )
    lines = rag_api.stream_event_lines(RerankRagChatRequest(question="测试"))

    first = json.loads(Context().run(next, lines))
    second = json.loads(Context().run(next, lines))

    assert first["type"] == "status"
    assert second["type"] == "done"
    assert second["model_usage"]["models"] == ["chat-test", "embedding-test"]
    assert second["model_usage"]["upstream_call_count"] == 2
    assert second["model_usage"]["total_tokens"] == 30

    with pytest.raises(StopIteration):
        Context().run(next, lines)
