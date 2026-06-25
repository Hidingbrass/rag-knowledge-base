"""RAG 问答编排服务。

这个文件负责把“检索 -> 可用片段判断 -> Prompt 构建 -> 模型生成”
串成完整的 RAG 问答流程。

为什么从 main.py 拆出来：
- main.py 只应该处理 HTTP 接口。
- 普通 RAG 和 Rerank RAG 都属于业务流程，适合放在 service 层。
- 评测脚本、后续 API 路由拆分、单元测试都可以复用这里的函数。
"""

import time

from app.core.exceptions import BadRequestError
from app.core.logging import get_logger
from app.schemas.rag import RagChatRequest, RerankRagChatRequest
from app.services.qdrant_service import search_chunks, hybrid_search_chunks
from app.services.qwen_service import chat_completion
from app.services.rerank_service import rerank_chunks

logger = get_logger(__name__)

# 当检索或 Rerank 判断资料不足时，统一返回这句拒答。
# 评测脚本会根据拒答短语判断系统是否正确拒答。
NO_RELEVANT_ANSWER = "知识库中没有足够相关的资料。"


def build_rag_messages(question: str, sources: list[dict]) -> list[dict]:
    """根据检索片段构建发送给大模型的 messages。

    参数：
    - question：用户原始问题。
    - sources：进入 Prompt 的资料片段列表。

    返回：
    - OpenAI-compatible chat messages，包含 system 和 user 两条消息。

    关键变量：
    - context_parts：每个 source 被格式化后的文本。
    - allowed_citations：允许模型引用的编号，例如 [1], [2], [3]。
    - context：最终拼进 Prompt 的参考资料全文。

    注意：
    - 普通 RAG source 只有 vector_score。
    - Rerank RAG source 会额外包含 rerank_score。
    """
    context_parts = []

    for index, source in enumerate(sources, start=1):
        if "rerank_score" in source:
            context_parts.append(
                f"[{index}] 来源文件: {source.get('filename')}, "
                f"页码: {source.get('page_number')}, "
                f"Chunk: {source.get('chunk_index')}, "
                f"向量相似度: {source.get('vector_score')}\n"
                f"Rerank 相关性: {source.get('rerank_score')}\n"
                f"{source.get('text')}"
            )
        else:
            context_parts.append(
                f"[{index}] 来源文件: {source.get('filename')}, "
                f"页码: {source.get('page_number')}, "
                f"Chunk: {source.get('chunk_index')}, "
                f"向量相似度: {source.get('vector_score')}\n"
                f"{source.get('text')}"
            )

    allowed_citations = ", ".join(
        f"[{index}]" for index in range(1, len(sources) + 1)
    )
    context = "\n\n".join(context_parts)

    return [
        {
            "role": "system",
            "content": (
                "你是一名严谨的知识库问答助手。"
                "请只根据用户提供的参考资料回答问题。"
                "如果参考资料中没有答案，请明确说明资料不足，不能编造。"
                "回答要简洁，并在关键结论后标注引用编号，例如 [1]。"
                f"当前允许使用的引用编号只有：{allowed_citations}。"
                "只能引用上述编号，不得编造不存在的引用编号。"
            ),
        },
        {
            "role": "user",
            "content": (
                f"参考资料:\n{context}\n\n"
                f"用户问题:\n{question}\n\n"
                "请基于参考资料回答。"
            ),
        },
    ]


def rag_chat_response(request: RagChatRequest) -> dict:
    """执行无 Rerank 的 RAG 问答流程。

    流程：
    1. 用向量检索召回 top_k 个 source。
    2. 用 min_score 过滤低相似度片段。
    3. 如果没有可用片段，直接拒答。
    4. 构建 Prompt。
    5. 调用通义千问生成答案。

    返回：
    - 与原 /rag/chat 接口保持一致的 dict。
    """
    sources = search_chunks(request.question, request.top_k, request.document_id)

    matched_sources = [
        source
        for source in sources
        if source["vector_score"] >= request.min_score
    ]
    logger.info(
        "普通 RAG 检索完成: top_k=%s min_score=%s candidate_count=%s matched_count=%s document_id=%s",
        request.top_k,
        request.min_score,
        len(sources),
        len(matched_sources),
        request.document_id,
    )

    if not matched_sources:
        logger.info("普通 RAG 拒答: reason=no_matched_sources")
        return {
            "question": request.question,
            "answer": NO_RELEVANT_ANSWER,
            "sources": [],
        }

    messages = build_rag_messages(request.question, matched_sources)
    answer = chat_completion(messages)
    logger.info("普通 RAG 生成完成: source_count=%s", len(matched_sources))

    return {
        "question": request.question,
        "answer": answer,
        "sources": matched_sources,
    }


def rag_chat_with_rerank_response(request: RerankRagChatRequest) -> dict:
    """执行带 Rerank 的 RAG 问答流程。

    流程：
    1. 向量检索先扩大候选集 candidate_k。
    2. 调用 qwen3-rerank 对候选片段重排。
    3. 如果 Rerank 空结果或最高分低于 rerank_min_score，拒答。
    4. 如果 Rerank 调用失败，走 vector_fallback。
    5. 用最终 matched_sources 构建 Prompt 并生成答案。

    返回：
    - 与原 /rag/chat/rerank 接口保持一致的 dict。

    可能抛出：
    - BadRequestError：当 rerank_top_k 大于 candidate_k 时抛出，由统一异常处理器转成 HTTP 400。
    """
    if request.rerank_top_k > request.candidate_k:
        raise BadRequestError("rerank_top_k 不能大于 candidate_k")

    rerank_elapsed_seconds = None
    if request.retrieval_mode == "vector":
        candidate_sources = search_chunks(
            request.question,
            request.candidate_k,
            request.document_id,
        )
    elif request.retrieval_mode == "hybrid":
        candidate_sources = hybrid_search_chunks(
            request.question,
            request.candidate_k,
            request.keyword_limit,
            request.document_id,
        )

    logger.info(
        "Rerank RAG 候选检索完成: candidate_k=%s candidate_count=%s document_id=%s",
        request.candidate_k,
        len(candidate_sources),
        request.document_id,
    )
    rerank_start_time = time.perf_counter()

    try:
        rerank_error = None
        reranked_sources = rerank_chunks(
            request.question,
            candidate_sources,
            request.rerank_top_k,
        )
        rerank_elapsed_seconds = time.perf_counter() - rerank_start_time
        retrieval_mode = "rerank"
        logger.info(
            "Rerank 调用完成: rerank_top_k=%s result_count=%s elapsed=%.4f",
            request.rerank_top_k,
            len(reranked_sources),
            rerank_elapsed_seconds,
        )

        if not reranked_sources:
            logger.info("Rerank RAG 拒答: reason=empty_rerank_result")
            return {
                "question": request.question,
                "answer": NO_RELEVANT_ANSWER,
                "sources": [],
                "retrieval_mode": retrieval_mode,
                "rerank_error": rerank_error,
                "rerank_elapsed_seconds": rerank_elapsed_seconds,
            }

        if reranked_sources[0]["rerank_score"] < request.rerank_min_score:
            logger.info(
                "Rerank RAG 拒答: reason=low_rerank_score top_score=%.4f threshold=%.4f",
                reranked_sources[0]["rerank_score"],
                request.rerank_min_score,
            )
            return {
                "question": request.question,
                "answer": NO_RELEVANT_ANSWER,
                "sources": [],
                "retrieval_mode": retrieval_mode,
                "rerank_error": rerank_error,
                "rerank_elapsed_seconds": rerank_elapsed_seconds,
            }

        matched_sources = reranked_sources
    except Exception as error:
        rerank_error = str(error)
        rerank_elapsed_seconds = time.perf_counter() - rerank_start_time
        retrieval_mode = "vector_fallback"
        logger.warning(
            "Rerank 调用失败，进入向量检索 fallback: error=%s elapsed=%.4f",
            rerank_error,
            rerank_elapsed_seconds,
        )
        matched_sources = [
            source
            for source in candidate_sources
            if (
                source.get("vector_score") is not None
                and source["vector_score"] >= request.fallback_min_score
            )
        ]

        if not matched_sources:
            logger.info("Rerank fallback 拒答: reason=no_matched_sources")
            return {
                "question": request.question,
                "answer": NO_RELEVANT_ANSWER,
                "sources": [],
                "retrieval_mode": retrieval_mode,
                "rerank_error": rerank_error,
                "rerank_elapsed_seconds": rerank_elapsed_seconds,
            }

    messages = build_rag_messages(request.question, matched_sources)
    answer = chat_completion(messages)
    logger.info(
        "Rerank RAG 生成完成: retrieval_mode=%s source_count=%s",
        retrieval_mode,
        len(matched_sources),
    )

    return {
        "question": request.question,
        "answer": answer,
        "sources": matched_sources,
        "retrieval_mode": retrieval_mode,
        "rerank_error": rerank_error,
        "rerank_elapsed_seconds": rerank_elapsed_seconds,
    }
