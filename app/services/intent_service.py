"""规则路由未命中后的轻量意图分类。"""

from __future__ import annotations

from pydantic import ValidationError

from app.core.config import settings
from app.core.exceptions import ExternalServiceError
from app.schemas.chat import IntentClassificationRequest, IntentClassifierOutput
from app.services.ai_safety_service import ensure_safe_model_input, wrap_untrusted_data
from app.services.qwen_service import chat_completion


CLASSIFIER_SYSTEM_PROMPT = """
你是企业知识库查询路由器。你只做分类，不回答用户问题。
请只输出 JSON，字段严格为：
{"intent":"KNOWLEDGE_QA|TOOL_CALL|CLARIFICATION|OPEN_DOMAIN_CHAT",
 "confidence":0.0,
 "enterprise_knowledge":true,
 "reason_code":"enterprise_knowledge|tool_request|needs_clarification|open_domain",
 "knowledge_scope":"ENTERPRISE|PUBLIC|UNKNOWN",
 "operation":"ANSWER|READ_TOOL|WRITE_TOOL|CLARIFY",
 "freshness":"REALTIME|STATIC|UNKNOWN",
 "tool_name":null,
 "missing_fields":[],
 "requires_confirmation":false}

分类规则：
1. KNOWLEDGE_QA：问题涉及用户所选资料、文档、知识库、公司/团队内部的制度、流程、数据、配置、项目、客户或业务事实；只要可能需要企业资料佐证，就选择它。
2. TOOL_CALL：明确要求查询实时外部信息，或执行发送、创建、修改、删除、导出等外部操作；但涉及当前资料的总结、练习题或解释仍是 KNOWLEDGE_QA。
3. CLARIFICATION：表达不完整，缺少对象、目标或必要参数，当前无法形成可回答的问题；指代当前资料或前文的表达仍是 KNOWLEDGE_QA。
4. OPEN_DOMAIN_CHAT：不依赖企业内部资料的常识、公开知识、创作、计算或一般交流。
5. 企业知识回答优先进入 KNOWLEDGE_QA。不确定、混合意图或置信度不足时选择 KNOWLEDGE_QA，并将 enterprise_knowledge 设为 true。
6. 删除、修改、发送、发布、创建、提交等外部写操作始终使用 TOOL_CALL + WRITE_TOOL，requires_confirmation=true；即使目标属于企业资料，也不能降级成知识回答。
7. 天气、汇率、股价、航班等实时查询使用 TOOL_CALL + READ_TOOL + REALTIME；缺少城市等参数时写入 missing_fields。
8. 企业知识使用 knowledge_scope=ENTERPRISE；公开知识使用 PUBLIC；无法判断使用 UNKNOWN。
9. 当前没有向你提供任何已授权工具，因此 tool_name 必须为 null；你只分类，不得声称已经执行操作。
10. reason_code 只描述分类类别，不输出推理过程或其他字段。
""".strip()


def classify_intent(request: IntentClassificationRequest) -> dict:
    """调用低成本模型分类，并在企业/低置信场景强制回到 RAG。"""
    ensure_safe_model_input(request.question)
    raw_output = chat_completion(
        [
            {"role": "system", "content": CLASSIFIER_SYSTEM_PROMPT},
            {
                "role": "user",
                "content": wrap_untrusted_data("query_to_classify", request.question),
            },
        ],
        model=settings.intent_model,
    )

    try:
        decision = IntentClassifierOutput.model_validate_json(raw_output)
    except (ValidationError, ValueError, TypeError) as error:
        raise ExternalServiceError(
            "意图分类器返回无效结果，请按知识库问答保守处理",
            details={"model": settings.intent_model},
        ) from error

    decision_source = "classifier"
    if decision.operation == "WRITE_TOOL":
        decision = decision.model_copy(update={
            "intent": "TOOL_CALL",
            "requires_confirmation": True,
            "reason_code": "tool_request",
        })
        decision_source = "write_action_guard"
    elif decision.enterprise_knowledge or decision.knowledge_scope == "ENTERPRISE":
        decision = decision.model_copy(update={
            "intent": "KNOWLEDGE_QA",
            "knowledge_scope": "ENTERPRISE",
            "operation": "ANSWER",
            "reason_code": "enterprise_knowledge",
        })
        decision_source = "enterprise_guard"
    elif decision.confidence < settings.intent_classifier_min_confidence:
        decision = decision.model_copy(update={
            "intent": "KNOWLEDGE_QA",
            "enterprise_knowledge": True,
            "knowledge_scope": "ENTERPRISE",
            "operation": "ANSWER",
            "reason_code": "enterprise_knowledge",
        })
        decision_source = "confidence_guard"
    elif decision.operation == "CLARIFY":
        decision = decision.model_copy(update={
            "intent": "CLARIFICATION",
            "reason_code": "needs_clarification",
        })

    return {
        **decision.model_dump(),
        "decision_source": decision_source,
    }
