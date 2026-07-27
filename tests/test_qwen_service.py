from types import SimpleNamespace

import pytest
from openai import OpenAIError

from app.core.exceptions import ExternalServiceError
from app.services import qwen_service


class FakeEmbeddings:
    def __init__(self, *, missing_last_vector: bool = False):
        self.calls: list[dict] = []
        self.missing_last_vector = missing_last_vector

    def create(self, **kwargs):
        self.calls.append(kwargs)
        data = [
            SimpleNamespace(index=index, embedding=[float(text.split("-")[-1])])
            for index, text in enumerate(kwargs["input"])
        ]
        if self.missing_last_vector:
            data = data[:-1]
        # 模拟上游乱序返回，验证业务结果仍按输入文本顺序合并。
        return SimpleNamespace(data=list(reversed(data)))


class FakeChatCompletions:
    def __init__(self):
        self.calls: list[dict] = []

    def create(self, **kwargs):
        self.calls.append(kwargs)
        return [
            SimpleNamespace(
                choices=[
                    SimpleNamespace(delta=SimpleNamespace(content="第一段"))
                ]
            ),
            SimpleNamespace(
                choices=[
                    SimpleNamespace(delta=SimpleNamespace(content=None))
                ]
            ),
            SimpleNamespace(
                choices=[
                    SimpleNamespace(delta=SimpleNamespace(content="第二段"))
                ]
            ),
        ]


class FakeSyncChatCompletions:
    def __init__(self, *, content='{"ok": true}', finish_reason="stop"):
        self.calls: list[dict] = []
        self.content = content
        self.finish_reason = finish_reason

    def create(self, **kwargs):
        self.calls.append(kwargs)
        return SimpleNamespace(
            usage=None,
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(content=self.content),
                    finish_reason=self.finish_reason,
                )
            ],
        )


def install_fake_client(monkeypatch, *, missing_last_vector: bool = False) -> FakeEmbeddings:
    embeddings = FakeEmbeddings(missing_last_vector=missing_last_vector)
    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(embeddings=embeddings),
    )
    return embeddings


def test_create_embeddings_splits_large_input_and_preserves_order(monkeypatch):
    embeddings = install_fake_client(monkeypatch)
    texts = [f"chunk-{index}" for index in range(45)]

    vectors = qwen_service.create_embeddings(texts)

    assert [len(call["input"]) for call in embeddings.calls] == [10, 10, 10, 10, 5]
    assert [vector[0] for vector in vectors] == [float(index) for index in range(45)]
    assert all(call["model"] == qwen_service.settings.embedding_model for call in embeddings.calls)
    assert all(
        call["dimensions"] == qwen_service.settings.embedding_dimensions
        for call in embeddings.calls
    )


def test_create_embeddings_uses_one_request_at_batch_boundary(monkeypatch):
    embeddings = install_fake_client(monkeypatch)

    vectors = qwen_service.create_embeddings([f"chunk-{index}" for index in range(10)])

    assert len(embeddings.calls) == 1
    assert len(vectors) == 10


def test_create_embeddings_returns_empty_without_calling_upstream(monkeypatch):
    embeddings = install_fake_client(monkeypatch)

    assert qwen_service.create_embeddings([]) == []
    assert embeddings.calls == []


def test_create_embeddings_rejects_incomplete_upstream_response(monkeypatch):
    install_fake_client(monkeypatch, missing_last_vector=True)

    with pytest.raises(RuntimeError, match="向量数量与输入文本数量不一致"):
        qwen_service.create_embeddings(["chunk-0", "chunk-1"])


def test_create_embeddings_converts_upstream_error_to_safe_business_error(monkeypatch):
    def raise_upstream_error(**kwargs):
        raise OpenAIError("raw upstream detail")

    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(embeddings=SimpleNamespace(create=raise_upstream_error)),
    )

    with pytest.raises(ExternalServiceError) as captured:
        qwen_service.create_embeddings(["chunk-0", "chunk-1"])

    assert captured.value.status_code == 502
    assert captured.value.message == "Embedding 服务调用失败，请稍后重试"
    assert captured.value.details == {
        "model": qwen_service.settings.embedding_model,
        "batch_size": 2,
    }


def test_chat_completion_stream_yields_non_empty_text_deltas(monkeypatch):
    completions = FakeChatCompletions()
    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(chat=SimpleNamespace(completions=completions)),
    )

    deltas = list(qwen_service.chat_completion_stream([
        {"role": "user", "content": "请回答"}
    ]))

    assert deltas == ["第一段", "第二段"]
    assert completions.calls == [{
        "model": qwen_service.settings.chat_model,
        "messages": [{"role": "user", "content": "请回答"}],
        "stream": True,
        "stream_options": {"include_usage": True},
        "max_tokens": qwen_service.settings.chat_max_output_tokens,
    }]


def test_chat_completion_stream_converts_upstream_error(monkeypatch):
    def raise_upstream_error(**kwargs):
        raise OpenAIError("raw upstream detail")

    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(create=raise_upstream_error)
            )
        ),
    )

    with pytest.raises(ExternalServiceError) as captured:
        list(qwen_service.chat_completion_stream([
            {"role": "user", "content": "请回答"}
        ]))

    assert captured.value.message == "大模型生成服务调用失败，请稍后重试"
    assert captured.value.details == {"model": qwen_service.settings.chat_model}


def test_chat_completion_enables_json_mode_for_structured_system_prompt(monkeypatch):
    completions = FakeSyncChatCompletions()
    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(chat=SimpleNamespace(completions=completions)),
    )

    answer = qwen_service.chat_completion([
        {
            "role": "system",
            "content": "你必须只输出 JSON，不要输出其他解释。",
        },
        {"role": "user", "content": "生成面试准备包"},
    ])

    assert answer == '{"ok": true}'
    assert completions.calls[0]["response_format"] == {"type": "json_object"}
    assert (
        completions.calls[0]["max_tokens"]
        == qwen_service.settings.structured_chat_max_output_tokens
    )


def test_chat_completion_keeps_plain_chat_out_of_json_mode(monkeypatch):
    completions = FakeSyncChatCompletions(content="普通回答")
    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(chat=SimpleNamespace(completions=completions)),
    )

    answer = qwen_service.chat_completion([
        {"role": "system", "content": "你是学习助手。"},
        {"role": "user", "content": "只输出 JSON 可以吗？"},
    ])

    assert answer == "普通回答"
    assert "response_format" not in completions.calls[0]
    assert completions.calls[0]["max_tokens"] == qwen_service.settings.chat_max_output_tokens


def test_structured_chat_reports_output_truncation(monkeypatch):
    completions = FakeSyncChatCompletions(finish_reason="length")
    monkeypatch.setattr(
        qwen_service,
        "client",
        SimpleNamespace(chat=SimpleNamespace(completions=completions)),
    )

    with pytest.raises(ExternalServiceError, match="结构化输出达到长度上限"):
        qwen_service.chat_completion([
            {"role": "system", "content": "请只输出 JSON 格式。"},
            {"role": "user", "content": "生成长内容"},
        ])
