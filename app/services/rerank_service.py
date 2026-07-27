"""qwen3-rerank 重排序服务。

这个文件只负责调用 Rerank API，并把 Rerank 结果映射回原始 Source。

Rerank 的核心作用：
- 向量检索先召回一批候选片段。
- Rerank 根据“问题和片段是否真正相关”重新排序。
- 排序后保留更能支持答案的片段进入 Prompt。
"""

import requests

from app.core.config import settings
from app.core.logging import get_logger
from app.services.ai_safety_service import mask_sensitive_data
from app.services.model_runtime import (
    ModelCallRecord,
    estimate_rerank_cost,
    execute_with_resilience,
    record_model_call,
)


logger = get_logger(__name__)
DOCUMENT_RRF_K = 60


def _document_key(source: dict, source_index: int) -> tuple[str, str | int]:
    """返回稳定的文档分组键；缺少文档元数据时把 Chunk 视为独立来源。"""
    document_id = source.get("document_id")
    if document_id:
        return "document_id", str(document_id)

    filename = source.get("filename")
    if filename:
        return "filename", str(filename)

    return "source_index", source_index


def select_diverse_sources(ranked_sources: list[dict], top_k: int) -> list[dict]:
    """使用跨阶段文档级 RRF 选择文档，再按 Rerank 顺序返回最佳 Chunk。

    文档分数同时考虑候选召回排名和 Rerank 排名，避免某个阶段完全覆盖另一个阶段的信号。
    先为入选文档各保留一个最高 Rerank Chunk；如果文档数不足，再按 Rerank 顺序补满 Top K。
    """
    if top_k <= 0 or not ranked_sources:
        return []

    document_scores: dict[tuple[str, str | int], float] = {}
    document_best_rerank: dict[tuple[str, str | int], int] = {}
    source_documents: list[tuple[str, str | int]] = []

    for rerank_index, source in enumerate(ranked_sources):
        document_key = _document_key(source, rerank_index)
        rerank_rank = rerank_index + 1
        retrieval_rank = int(source.get("_retrieval_rank", rerank_index)) + 1
        cross_stage_score = (
            1 / (DOCUMENT_RRF_K + rerank_rank)
            + 1 / (DOCUMENT_RRF_K + retrieval_rank)
        )
        source_documents.append(document_key)

        if cross_stage_score > document_scores.get(document_key, -1.0):
            document_scores[document_key] = cross_stage_score
        document_best_rerank[document_key] = min(
            rerank_rank,
            document_best_rerank.get(document_key, rerank_rank),
        )

    ranked_documents = sorted(
        document_scores,
        key=lambda key: (
            -document_scores[key],
            document_best_rerank[key],
        ),
    )
    selected_documents = ranked_documents[:top_k]
    selected: list[dict] = []
    selected_indexes: set[int] = set()

    for selected_document in selected_documents:
        for source_index, (source, document_key) in enumerate(
                zip(ranked_sources, source_documents),
        ):
            if document_key != selected_document:
                continue

            selected.append(source)
            selected_indexes.add(source_index)
            break

    for source_index, source in enumerate(ranked_sources):
        if len(selected) == top_k:
            break
        if source_index in selected_indexes:
            continue

        selected.append(source)

    public_sources = []
    for source in selected:
        public_source = source.copy()
        public_source.pop("_retrieval_rank", None)
        public_sources.append(public_source)
    return public_sources


def rerank_chunks(
        question: str,
        sources: list[dict],
        rerank_top_k: int,
        diversify_by_document: bool | None = None,
) -> list[dict]:
    """调用 qwen3-rerank 对候选 source 重新排序。

    参数：
    - question：用户问题。
    - sources：向量检索召回的候选片段，每个 source 至少要包含 text。
    - rerank_top_k：Rerank 后最多返回多少个片段。
    - diversify_by_document：是否优先保留不同文档；None 时读取全局配置。

    返回：
    - 重排后的 source 列表。
    - 每个返回 source 都是原始 source 的 copy，并新增 rerank_score。

    关键变量：
    - documents：发给 Rerank API 的纯文本列表。
    - source_index：Rerank 返回的原始 documents 下标。
    - reranked_source：复制出来的新 source，避免污染原始 sources。
    """
    if not sources:
        logger.info("Rerank 跳过: reason=empty_sources")
        return []

    diversity_enabled = (
        settings.rerank_document_diversity_enabled
        if diversify_by_document is None
        else diversify_by_document
    )
    api_top_n = len(sources) if diversity_enabled else rerank_top_k

    documents = [
        mask_sensitive_data(source["text"])
        for source in sources
    ]

    headers = {
        "Authorization": f"Bearer {settings.dashscope_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.rerank_model,
        "query": mask_sensitive_data(question),
        "documents": documents,
        "top_n": api_top_n,
    }

    def call_rerank():
        response = requests.post(
            settings.rerank_api_url,
            headers=headers,
            json=payload,
            timeout=settings.request_timeout_seconds,
        )
        response.raise_for_status()
        return response

    try:
        response, attempts, elapsed_ms = execute_with_resilience(
            call_rerank,
            circuit_key=f"rerank:{settings.rerank_model}",
            is_retryable=_is_retryable_rerank_error,
        )
    except Exception as error:
        record_model_call(ModelCallRecord(
            model=settings.rerank_model,
            operation="rerank",
            success=False,
            attempts=int(getattr(error, "_model_attempts", 1)),
            elapsed_ms=int(getattr(error, "_model_elapsed_ms", 0)),
        ))
        raise

    response_data = response.json()
    usage = response_data.get("usage") or {}
    total_tokens = int(
        usage.get("total_tokens")
        or usage.get("input_tokens")
        or usage.get("prompt_tokens")
        or 0
    )
    record_model_call(ModelCallRecord(
        model=settings.rerank_model,
        operation="rerank",
        success=True,
        attempts=attempts,
        elapsed_ms=elapsed_ms,
        prompt_tokens=total_tokens,
        total_tokens=total_tokens,
        estimated_cost_yuan=estimate_rerank_cost(total_tokens),
    ))
    rerank_results = response_data["results"]
    rerank_sources = []
    logger.info(
        "Rerank API 返回成功: candidate_count=%s top_n=%s result_count=%s diversity=%s",
        len(sources),
        api_top_n,
        len(rerank_results),
        diversity_enabled,
    )

    for rerank_result in rerank_results:
        source_index = rerank_result["index"]
        rerank_score = rerank_result["relevance_score"]

        original_source = sources[source_index]
        reranked_source = original_source.copy()
        reranked_source["rerank_score"] = rerank_score
        if diversity_enabled:
            reranked_source["_retrieval_rank"] = source_index
        rerank_sources.append(reranked_source)

    selected_sources = (
        select_diverse_sources(rerank_sources, rerank_top_k)
        if diversity_enabled
        else rerank_sources[:rerank_top_k]
    )
    logger.info(
        "Rerank 结果选择完成: ranked_count=%s selected_count=%s unique_documents=%s",
        len(rerank_sources),
        len(selected_sources),
        len({
            _document_key(source, source_index)
            for source_index, source in enumerate(selected_sources)
        }),
    )
    return selected_sources


def _is_retryable_rerank_error(error: Exception) -> bool:
    if isinstance(error, (requests.Timeout, requests.ConnectionError)):
        return True
    if isinstance(error, requests.HTTPError):
        status_code = error.response.status_code if error.response is not None else None
        return status_code == 429 or (
            isinstance(status_code, int)
            and status_code >= 500
        )
    return False
