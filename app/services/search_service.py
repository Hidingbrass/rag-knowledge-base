"""搜索演示和 Embedding 测试服务。

这个文件不是正式 RAG 检索链路。

它主要服务两个学习接口：
- /search/demo：用几条写死文本演示余弦相似度排序。
- /embedding/test：测试单条文本 Embedding 是否正常。
"""

import math

from app.schemas.search import SearchRequest
from app.services.qwen_service import create_embedding, create_embeddings


def cosine_similarity(vector_a: list[float], vector_b: list[float]) -> float:
    """计算两个向量的余弦相似度。

    参数：
    - vector_a：第一个向量。
    - vector_b：第二个向量。

    返回：
    - 两个向量的余弦相似度。

    注意：
    - 这里只用于 /search/demo。
    - 正式 RAG 检索使用 qdrant_service.search_chunks 返回的 vector_score。
    """
    dot_product = sum(a * b for a, b in zip(vector_a, vector_b))
    magnitude_a = math.sqrt(sum(a * a for a in vector_a))
    magnitude_b = math.sqrt(sum(b * b for b in vector_b))

    if magnitude_a == 0 or magnitude_b == 0:
        return 0.0

    return dot_product / (magnitude_a * magnitude_b)


def search_demo_response(request: SearchRequest) -> dict:
    """执行本地向量相似度演示。

    返回字段：
    - score：演示接口独立使用的相似度分数。

    注意：
    - 这里不要改成 vector_score。
    - vector_score 是 RAG source 中的字段。
    """
    chunks = [
        "星河科技的平台由 Java SpringBoot 业务服务和 Python FastAPI 智能服务组成。",
        "top-k 太大可能带入无关片段，增加 token 消耗，并干扰模型生成结果。",
        "Redis 可以用于缓存热点数据、保存短期会话信息以及实现简单限流。",
    ]

    embeddings = create_embeddings([request.question, *chunks])
    question_vector = embeddings[0]
    chunk_vectors = embeddings[1:]
    results = []

    for chunk, chunk_vector in zip(chunks, chunk_vectors):
        score = cosine_similarity(question_vector, chunk_vector)

        results.append(
            {
                "chunk": chunk,
                "score": round(score, 4),
            }
        )

    results.sort(key=lambda item: item["score"], reverse=True)

    return {
        "question": request.question,
        "embedding_dimensions": len(question_vector),
        "results": results,
    }


def embedding_test_response(request: SearchRequest) -> dict:
    """执行单条文本 Embedding 测试。

    返回：
    - text：原始文本。
    - embedding_dimensions：向量维度。
    - preview：向量前 5 个数值，方便人工确认接口正常。
    """
    vector = create_embedding(request.question)

    return {
        "text": request.question,
        "embedding_dimensions": len(vector),
        "preview": vector[:5],
    }
