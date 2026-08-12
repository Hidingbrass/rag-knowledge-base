import pytest

from app.core.exceptions import ExternalServiceError
from app.schemas.chat import ChatRequest, IntentClassificationRequest
from app.services import chat_service, intent_service


def test_open_domain_intent_uses_lightweight_model(monkeypatch):
    captured = {}

    def fake_chat_completion(messages, model=None, temperature=None):
        captured["messages"] = messages
        captured["model"] = model
        captured["temperature"] = temperature
        return """{
          "intent": "OPEN_DOMAIN_CHAT",
          "confidence": 0.96,
          "enterprise_knowledge": false,
          "reason_code": "open_domain"
        }"""

    monkeypatch.setattr(intent_service, "chat_completion", fake_chat_completion)

    result = intent_service.classify_intent(
        IntentClassificationRequest(question="1+1 等于多少？")
    )

    assert result["intent"] == "OPEN_DOMAIN_CHAT"
    assert result["decision_source"] == "classifier"
    assert captured["model"] == intent_service.settings.intent_model
    assert captured["temperature"] == 0
    assert "只做分类" in captured["messages"][0]["content"]


def test_enterprise_flag_overrides_non_rag_label(monkeypatch):
    monkeypatch.setattr(
        intent_service,
        "chat_completion",
        lambda messages, model=None, temperature=None: """{
          "intent": "OPEN_DOMAIN_CHAT",
          "confidence": 0.99,
          "enterprise_knowledge": true,
          "reason_code": "open_domain"
        }""",
    )

    result = intent_service.classify_intent(
        IntentClassificationRequest(question="我们公司的报销制度是什么？")
    )

    assert result["intent"] == "KNOWLEDGE_QA"
    assert result["enterprise_knowledge"] is True
    assert result["decision_source"] == "enterprise_guard"


def test_low_confidence_routes_back_to_rag(monkeypatch):
    monkeypatch.setattr(
        intent_service,
        "chat_completion",
        lambda messages, model=None, temperature=None: """{
          "intent": "TOOL_CALL",
          "confidence": 0.51,
          "enterprise_knowledge": false,
          "reason_code": "tool_request"
        }""",
    )

    result = intent_service.classify_intent(
        IntentClassificationRequest(question="处理一下")
    )

    assert result["intent"] == "KNOWLEDGE_QA"
    assert result["enterprise_knowledge"] is True
    assert result["decision_source"] == "confidence_guard"


def test_invalid_classifier_output_returns_safe_error(monkeypatch):
    monkeypatch.setattr(
        intent_service,
        "chat_completion",
        lambda messages, model=None, temperature=None: "not-json",
    )

    with pytest.raises(ExternalServiceError, match="按知识库问答保守处理"):
        intent_service.classify_intent(
            IntentClassificationRequest(question="这个怎么处理？")
        )


def test_write_tool_requires_confirmation(monkeypatch):
    monkeypatch.setattr(
        intent_service,
        "chat_completion",
        lambda messages, model=None, temperature=None: """{
          "intent": "TOOL_CALL",
          "confidence": 0.97,
          "enterprise_knowledge": false,
          "reason_code": "tool_request",
          "knowledge_scope": "UNKNOWN",
          "operation": "WRITE_TOOL",
          "freshness": "STATIC",
          "tool_name": null,
          "missing_fields": [],
          "requires_confirmation": false
        }""",
    )

    result = intent_service.classify_intent(
        IntentClassificationRequest(question="帮我删除这份文档")
    )

    assert result["intent"] == "TOOL_CALL"
    assert result["operation"] == "WRITE_TOOL"
    assert result["requires_confirmation"] is True
    assert "delete_knowledge_document" in intent_service.CLASSIFIER_SYSTEM_PROMPT


def test_realtime_read_tool_keeps_missing_fields(monkeypatch):
    monkeypatch.setattr(
        intent_service,
        "chat_completion",
        lambda messages, model=None, temperature=None: """{
          "intent": "TOOL_CALL",
          "confidence": 0.95,
          "enterprise_knowledge": false,
          "reason_code": "tool_request",
          "knowledge_scope": "PUBLIC",
          "operation": "READ_TOOL",
          "freshness": "REALTIME",
          "tool_name": null,
          "missing_fields": ["city"],
          "requires_confirmation": false
        }""",
    )

    result = intent_service.classify_intent(
        IntentClassificationRequest(question="帮我查天气")
    )

    assert result["operation"] == "READ_TOOL"
    assert result["freshness"] == "REALTIME"
    assert result["missing_fields"] == ["city"]


def test_tool_mode_forbids_fabricated_execution(monkeypatch):
    captured = {}

    def fake_chat_completion(messages):
        captured["messages"] = messages
        return "请提供城市。"

    monkeypatch.setattr(chat_service, "chat_completion", fake_chat_completion)

    result = chat_service.chat_response(ChatRequest(
        question="帮我查天气",
        mode="tool_call",
    ))

    assert result == {"answer": "请提供城市。"}
    assert "不得声称已经查询" in captured["messages"][0]["content"]
