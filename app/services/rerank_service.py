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


logger = get_logger(__name__)


def rerank_chunks(question: str, sources: list[dict], rerank_top_k: int) -> list[dict]:
    """调用 qwen3-rerank 对候选 source 重新排序。

    参数：
    - question：用户问题。
    - sources：向量检索召回的候选片段，每个 source 至少要包含 text。
    - rerank_top_k：Rerank 后最多返回多少个片段。

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

    documents = [
        source["text"]
        for source in sources
    ]

    headers = {
        "Authorization": f"Bearer {settings.dashscope_api_key}",
        "Content-Type": "application/json",
    }
    payload = {
        "model": settings.rerank_model,
        "query": question,
        "documents": documents,
        "top_n": rerank_top_k,
    }

    response = requests.post(
        settings.rerank_api_url,
        headers=headers,
        json=payload,
        timeout=settings.request_timeout_seconds,
    )
    response.raise_for_status()

    response_data = response.json()
    rerank_results = response_data["results"]
    rerank_sources = []
    logger.info(
        "Rerank API 返回成功: candidate_count=%s top_n=%s result_count=%s",
        len(sources),
        rerank_top_k,
        len(rerank_results),
    )

    for rerank_result in rerank_results:
        source_index = rerank_result["index"]
        rerank_score = rerank_result["relevance_score"]

        original_source = sources[source_index]
        reranked_source = original_source.copy()
        reranked_source["rerank_score"] = rerank_score
        rerank_sources.append(reranked_source)

    return rerank_sources
