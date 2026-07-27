"""通义千问模型调用服务。

这个文件集中封装 DashScope OpenAI-compatible API 的调用：
- create_embedding：单条文本向量化。
- create_embeddings：批量文本向量化。
- chat_completion：调用 qwen-plus 生成回答。
- chat_completion_with_images：调用视觉模型识别截图或扫描 PDF。

把模型调用放在 service 层，可以避免 main.py 直接依赖模型客户端细节。
"""

import base64
import time

from openai import OpenAI, OpenAIError

from app.core.config import settings
from app.core.exceptions import ExternalServiceError
from app.services.model_runtime import (
    ModelCallRecord,
    ModelCircuitOpenError,
    estimate_chat_cost,
    estimate_embedding_cost,
    execute_with_resilience,
    record_circuit_failure,
    record_model_call,
)


# client 是 OpenAI-compatible 客户端，实际请求会发送到 DashScope。
client = OpenAI(
    api_key=settings.dashscope_api_key,
    base_url=settings.dashscope_base_url,
    timeout=settings.request_timeout_seconds,
)

# DashScope text-embedding-v4 的 OpenAI-compatible 接口单次最多接收 10 条文本。
# 在模型调用封装层统一分批，避免文档入库和搜索演示等上层调用重复处理该限制。
EMBEDDING_BATCH_SIZE = 10


def _usage_values(response) -> tuple[int, int, int]:
    usage = getattr(response, "usage", None)
    if usage is None:
        return 0, 0, 0
    prompt_tokens = int(
        getattr(usage, "prompt_tokens", None)
        or getattr(usage, "input_tokens", None)
        or 0
    )
    completion_tokens = int(
        getattr(usage, "completion_tokens", None)
        or getattr(usage, "output_tokens", None)
        or 0
    )
    total_tokens = int(
        getattr(usage, "total_tokens", None)
        or prompt_tokens + completion_tokens
    )
    return prompt_tokens, completion_tokens, total_tokens


def _is_retryable_openai_error(error: Exception) -> bool:
    class_name = type(error).__name__
    if class_name in {"APIConnectionError", "APITimeoutError", "RateLimitError", "InternalServerError"}:
        return True
    status_code = getattr(error, "status_code", None)
    return status_code == 429 or (isinstance(status_code, int) and status_code >= 500)


def _failed_call_record(model: str, operation: str, error: Exception) -> None:
    record_model_call(ModelCallRecord(
        model=model,
        operation=operation,
        success=False,
        attempts=int(getattr(error, "_model_attempts", 1)),
        elapsed_ms=int(getattr(error, "_model_elapsed_ms", 0)),
    ))


def _requires_json_response(messages: list[dict]) -> bool:
    """识别项目内明确声明“只输出 JSON”的结构化任务。"""
    return any(
        message.get("role") == "system"
        and isinstance(message.get("content"), str)
        and "JSON" in message["content"].upper()
        and (
            "只输出" in message["content"]
            or "JSON 格式" in message["content"]
            or "JSON格式" in message["content"]
        )
        for message in messages
    )


def _request_embeddings(texts: str | list[str]):
    """调用 DashScope Embedding，并把上游异常转换为安全的业务错误。"""
    batch_size = 1 if isinstance(texts, str) else len(texts)
    try:
        response, attempts, elapsed_ms = execute_with_resilience(
            lambda: client.embeddings.create(
                model=settings.embedding_model,
                input=texts,
                dimensions=settings.embedding_dimensions,
                encoding_format="float",
            ),
            circuit_key=f"embedding:{settings.embedding_model}",
            is_retryable=_is_retryable_openai_error,
        )
        prompt_tokens, completion_tokens, total_tokens = _usage_values(response)
        record_model_call(ModelCallRecord(
            model=settings.embedding_model,
            operation="embedding",
            success=True,
            attempts=attempts,
            elapsed_ms=elapsed_ms,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
            estimated_cost_yuan=estimate_embedding_cost(total_tokens),
        ))
        return response
    except (OpenAIError, ModelCircuitOpenError) as error:
        _failed_call_record(settings.embedding_model, "embedding", error)
        raise ExternalServiceError(
            "Embedding 服务调用失败，请稍后重试",
            details={
                "model": settings.embedding_model,
                "batch_size": batch_size,
            },
        ) from error


def create_embedding(text: str) -> list[float]:
    """为单条文本生成 Embedding 向量。

    参数：
    - text：需要向量化的文本。

    返回：
    - settings.embedding_dimensions 维 float 向量。

    使用位置：
    - /embedding/test
    - qdrant_service.search_chunks()
    """
    response = _request_embeddings(text)

    return response.data[0].embedding


def create_embeddings(texts: list[str]) -> list[list[float]]:
    """为多条文本批量生成 Embedding 向量。

    参数：
    - texts：文本列表，通常是多个 chunk。

    返回：
    - 向量列表，顺序与输入 texts 保持一致。

    使用位置：
    - 文档入库
    - /search/demo 演示
    """
    if not texts:
        return []

    vectors: list[list[float]] = []
    for start in range(0, len(texts), EMBEDDING_BATCH_SIZE):
        batch = texts[start:start + EMBEDDING_BATCH_SIZE]
        response = _request_embeddings(batch)
        ordered_items = sorted(response.data, key=lambda item: item.index)
        if len(ordered_items) != len(batch):
            raise RuntimeError(
                "Embedding 服务返回的向量数量与输入文本数量不一致: "
                f"expected={len(batch)} actual={len(ordered_items)}"
            )
        vectors.extend(item.embedding for item in ordered_items)

    return vectors


def chat_completion(messages: list[dict], model: str | None = None) -> str:
    """调用 qwen-plus 生成聊天回答。

    参数：
    - messages：OpenAI-compatible chat messages。

    返回：
    - 模型生成的文本答案。

    使用位置：
    - /chat
    - rag_service 中的普通 RAG 和 Rerank RAG。
    """
    selected_model = model or settings.chat_model
    json_response = _requires_json_response(messages)
    request_options = {
        "model": selected_model,
        "messages": messages,
        "max_tokens": (
            settings.structured_chat_max_output_tokens
            if json_response
            else settings.chat_max_output_tokens
        ),
    }
    if json_response:
        # DashScope OpenAI-compatible JSON mode。Prompt 已明确包含 JSON，
        # 满足上游接口要求，并防止 Markdown/额外解释破坏后续解析。
        request_options["response_format"] = {"type": "json_object"}

    try:
        completion, attempts, elapsed_ms = execute_with_resilience(
            lambda: client.chat.completions.create(**request_options),
            circuit_key=f"chat:{selected_model}",
            is_retryable=_is_retryable_openai_error,
        )
        prompt_tokens, completion_tokens, total_tokens = _usage_values(completion)
        record_model_call(ModelCallRecord(
            model=selected_model,
            operation="chat_completion",
            success=True,
            attempts=attempts,
            elapsed_ms=elapsed_ms,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
            estimated_cost_yuan=estimate_chat_cost(
                prompt_tokens,
                completion_tokens,
            ),
        ))
        choice = completion.choices[0]
        if json_response and getattr(choice, "finish_reason", None) == "length":
            raise ExternalServiceError(
                "模型结构化输出达到长度上限，请缩短输入后重试",
                details={
                    "model": selected_model,
                    "max_output_tokens": settings.structured_chat_max_output_tokens,
                },
            )
        return choice.message.content
    except (OpenAIError, ModelCircuitOpenError) as error:
        _failed_call_record(selected_model, "chat_completion", error)
        raise ExternalServiceError(
            "大模型生成服务调用失败，请稍后重试",
            details={"model": selected_model},
        ) from error


def chat_completion_stream(messages: list[dict]):
    """流式生成聊天回答，只向上层产出非空文本增量。

    OpenAI-compatible 的 ``stream=True`` 会返回一组增量事件。这里集中处理
    DashScope 的客户端细节，让 RAG 服务只需要迭代字符串，不依赖 SDK 响应结构。
    """
    started_at = time.perf_counter()
    emitted_content = False
    final_usage = (0, 0, 0)
    try:
        completion, attempts, _ = execute_with_resilience(
            lambda: client.chat.completions.create(
                model=settings.chat_model,
                messages=messages,
                stream=True,
                stream_options={"include_usage": True},
                max_tokens=settings.chat_max_output_tokens,
            ),
            circuit_key=f"chat_stream:{settings.chat_model}",
            is_retryable=_is_retryable_openai_error,
        )
        for chunk in completion:
            chunk_usage = _usage_values(chunk)
            if chunk_usage != (0, 0, 0):
                final_usage = chunk_usage
            if not chunk.choices:
                continue
            content = chunk.choices[0].delta.content
            if content:
                emitted_content = True
                yield content
        elapsed_ms = max(0, int((time.perf_counter() - started_at) * 1000))
        prompt_tokens, completion_tokens, total_tokens = final_usage
        record_model_call(ModelCallRecord(
            model=settings.chat_model,
            operation="chat_completion_stream",
            success=True,
            attempts=attempts,
            elapsed_ms=elapsed_ms,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
            estimated_cost_yuan=estimate_chat_cost(
                prompt_tokens,
                completion_tokens,
            ),
        ))
    except (OpenAIError, ModelCircuitOpenError) as error:
        # 已经输出 Token 后不自动重放，避免前端收到重复内容。
        if emitted_content:
            setattr(error, "_model_attempts", 1)
        if emitted_content or not hasattr(error, "_model_attempts"):
            record_circuit_failure(f"chat_stream:{settings.chat_model}")
        _failed_call_record(settings.chat_model, "chat_completion_stream", error)
        raise ExternalServiceError(
            "大模型生成服务调用失败，请稍后重试",
            details={"model": settings.chat_model},
        ) from error


def chat_completion_with_images(prompt: str, images: list[tuple[bytes, str]]) -> str:
    """调用视觉模型，从图片中提取或理解文本。

    参数：
    - prompt：告诉视觉模型要完成什么任务。
    - images：图片字节和 MIME 类型列表，例如 [(content, "image/png")]。

    返回：
    - 模型生成的文本。
    """
    content = [{"type": "text", "text": prompt}]
    for image_content, mime_type in images:
        encoded_image = base64.b64encode(image_content).decode("ascii")
        content.append({
            "type": "image_url",
            "image_url": {
                "url": f"data:{mime_type};base64,{encoded_image}",
            },
        })

    try:
        completion, attempts, elapsed_ms = execute_with_resilience(
            lambda: client.chat.completions.create(
                model=settings.vision_model,
                messages=[
                    {
                        "role": "user",
                        "content": content,
                    }
                ],
                max_tokens=settings.chat_max_output_tokens,
            ),
            circuit_key=f"vision:{settings.vision_model}",
            is_retryable=_is_retryable_openai_error,
        )
        prompt_tokens, completion_tokens, total_tokens = _usage_values(completion)
        record_model_call(ModelCallRecord(
            model=settings.vision_model,
            operation="vision_completion",
            success=True,
            attempts=attempts,
            elapsed_ms=elapsed_ms,
            prompt_tokens=prompt_tokens,
            completion_tokens=completion_tokens,
            total_tokens=total_tokens,
            estimated_cost_yuan=estimate_chat_cost(prompt_tokens, completion_tokens),
        ))
        return completion.choices[0].message.content
    except (OpenAIError, ModelCircuitOpenError) as error:
        _failed_call_record(settings.vision_model, "vision_completion", error)
        raise ExternalServiceError(
            "图片识别服务调用失败，请稍后重试",
            details={"model": settings.vision_model},
        ) from error
