"""Qdrant 向量数据库服务。

这个文件集中管理所有直接访问 Qdrant 的代码。

放在这里的原因：
- main.py 不需要知道 Qdrant 的底层 API 细节。
- RAG、文档管理、评测脚本可以复用同一套检索和文档操作函数。
- 后续如果更换 collection 名称、向量维度或 Qdrant 地址，只需要优先检查这里。
"""
import re
from uuid import uuid4

from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams
from qdrant_client.models import Filter, FieldCondition, MatchValue, FilterSelector, PointStruct

from app.core.config import settings
from app.core.logging import get_logger
from app.services.qwen_service import create_embedding

logger = get_logger(__name__)

# qdrant_client 是全项目复用的 Qdrant 客户端。
# 地址来自 settings.qdrant_url，避免把本地地址写死在业务代码里。
qdrant_client = QdrantClient(url=settings.qdrant_url)

# COLLECTION_NAME 是保存 RAG 文本片段向量的集合名。
# 通过配置管理后，测试环境和正式环境可以使用不同 collection。
COLLECTION_NAME = settings.qdrant_collection_name


def ensure_collection():
    """确保 RAG 使用的 Qdrant Collection 已存在。

    输入：无。
    输出：无显式返回。
    用途：上传文档入库前调用，避免 collection 不存在导致 upsert 失败。
    """
    collections = qdrant_client.get_collections().collections
    collection_names = [collection.name for collection in collections]

    if COLLECTION_NAME not in collection_names:
        logger.info(
            "Qdrant collection 不存在，开始创建: collection=%s vector_size=%s",
            COLLECTION_NAME,
            settings.embedding_dimensions,
        )
        qdrant_client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(
                size=settings.embedding_dimensions,
                distance=Distance.COSINE,
            ),
        )
        logger.info("Qdrant collection 创建完成: collection=%s", COLLECTION_NAME)


def reset_collection():
    """重建 RAG Collection。

    输入：无。
    输出：无显式返回。
    用途：调试或重新索引测试文档时使用，会删除旧 collection 后重新创建。
    注意：这是破坏性操作，后续如果暴露为接口需要增加权限保护。
    """
    collections = qdrant_client.get_collections().collections
    collection_names = [collection.name for collection in collections]

    if COLLECTION_NAME in collection_names:
        logger.warning("开始删除 Qdrant collection: collection=%s", COLLECTION_NAME)
        qdrant_client.delete_collection(collection_name=COLLECTION_NAME)

    qdrant_client.create_collection(
        collection_name=COLLECTION_NAME,
        vectors_config=VectorParams(
            size=settings.embedding_dimensions,
            distance=Distance.COSINE,
        ),
    )
    logger.info("Qdrant collection 重建完成: collection=%s", COLLECTION_NAME)


def search_chunks(question: str, top_k: int, document_id: str | None = None):
    """根据用户问题从 Qdrant 检索最相近的文本片段。

    参数：
    - question：用户问题，会先转成 Embedding 向量。
    - top_k：向量检索最多返回多少个候选片段。
    - document_id：可选，只在指定文档范围内检索。

    返回：
    - RAG Source 列表，每个 source 都包含 text、filename、page_number、
      chunk_index 和 vector_score。

    注意：
    - 这里返回的是 RAG 链路使用的 vector_score。
    - /search/demo 中独立演示用的 score 不在这里处理。
    """
    question_vector = create_embedding(question)

    query_filter = None

    if document_id is not None:
        query_filter = Filter(
            must=[
                FieldCondition(
                    key="document_id",
                    match=MatchValue(value=document_id),
                )
            ]
        )

    response = qdrant_client.query_points(
        collection_name=COLLECTION_NAME,
        query=question_vector,
        limit=top_k,
        query_filter=query_filter,
    )
    logger.info(
        "Qdrant 检索完成: collection=%s top_k=%s document_id=%s result_count=%s",
        COLLECTION_NAME,
        top_k,
        document_id,
        len(response.points),
    )

    sources = []

    for result in response.points:
        sources.append(
            {
                "document_id": result.payload.get("document_id"),
                "vector_score": round(result.score, 4),
                "text": result.payload.get("text"),
                "filename": result.payload.get("filename"),
                "chunk_index": result.payload.get("chunk_index"),
                "page_number": result.payload.get("page_number"),
            }
        )

    return sources


def find_document_by_hash(file_hash: str):
    """根据文件 SHA-256 哈希查找已入库文档。

    参数：
    - file_hash：上传文件内容计算出的 SHA-256。

    返回：
    - 如果已存在同 hash 文档，返回该文档任意一个 point 的 payload。
    - 如果不存在，返回 None。

    用途：
    - 上传文档前做重复检测，避免同一 PDF 被重复入库。
    """
    if not qdrant_client.collection_exists(COLLECTION_NAME):
        return None
    points, _ = qdrant_client.scroll(
        collection_name=COLLECTION_NAME,
        scroll_filter=Filter(
            must=[
                FieldCondition(
                    key="file_hash",
                    match=MatchValue(value=file_hash),
                )
            ]
        ),
        limit=1,
        with_payload=True,
        with_vectors=False,
    )
    if not points:
        return None

    return points[0].payload


def delete_document_points(document_id: str):
    """删除某个 document_id 对应的全部 Qdrant points。

    参数：
    - document_id：文档唯一标识。

    输出：
    - 无显式返回；删除失败会由 Qdrant 客户端抛出异常。
    """
    qdrant_client.delete(
        collection_name=COLLECTION_NAME,
        points_selector=FilterSelector(
            filter=Filter(
                must=[
                    FieldCondition(
                        key="document_id",
                        match=MatchValue(value=document_id),
                    )
                ]
            )
        ),
        wait=True,
    )
    logger.info("Qdrant 文档删除完成: document_id=%s", document_id)


def list_indexed_documents() -> list[dict]:
    """分页扫描 Qdrant，按 document_id 聚合已入库文档。

    返回：
    - 文档列表，每个元素包含 document_id、filename、chunk_count。

    关键变量：
    - offset：Qdrant scroll 的分页游标。
    - documents：临时字典，用 document_id 聚合多个 chunk。
    """
    offset = None
    documents = {}

    while True:
        points, next_offset = qdrant_client.scroll(
            collection_name=COLLECTION_NAME,
            limit=100,
            offset=offset,
            with_payload=True,
            with_vectors=False,
        )

        for point in points:
            document_id = point.payload.get("document_id")

            if document_id not in documents:
                documents[document_id] = {
                    "document_id": document_id,
                    "filename": point.payload.get("filename"),
                    "chunk_count": 1,
                }
            else:
                documents[document_id]["chunk_count"] += 1

        if next_offset is None:
            break

        offset = next_offset

    return list(documents.values())


def upsert_document_chunks(
        chunks: list[dict],
        vectors: list[list[float]],
        filename: str,
        file_hash: str,
) -> str:
    """把文档 chunks 和对应向量写入 Qdrant。

    参数：
    - chunks：PDF 切分后的片段列表，每个元素包含 text 和 page_number。
    - vectors：每个 chunk 对应的 Embedding 向量。
    - filename：原始文件名，作为引用来源展示。
    - file_hash：文件 SHA-256，用于重复检测。

    返回：
    - document_id：本次入库生成的文档唯一标识。

    关键变量：
    - document_id：同一个 PDF 的所有 chunk 共用一个 document_id。
    - point_id：Qdrant 中每个 chunk point 的唯一 id。
    - points：准备批量 upsert 到 Qdrant 的 PointStruct 列表。
    """
    ensure_collection()

    document_id = str(uuid4())
    points = []

    for index, (chunk, vector) in enumerate(zip(chunks, vectors)):
        point_id = str(uuid4())
        points.append(
            PointStruct(
                id=point_id,
                vector=vector,
                payload={
                    "document_id": document_id,
                    "text": chunk["text"],
                    "page_number": chunk["page_number"],
                    "filename": filename,
                    "chunk_index": index,
                    "file_hash": file_hash,
                },
            )
        )

    qdrant_client.upsert(
        collection_name=COLLECTION_NAME,
        points=points,
    )
    logger.info(
        "Qdrant chunks 写入完成: collection=%s document_id=%s point_count=%s",
        COLLECTION_NAME,
        document_id,
        len(points),
    )

    return document_id


def extract_keywords(question):
    keywords = re.findall(r"[\u4e00-\u9fff]+|[a-zA-Z0-9_]+", question)
    return [
        keyword
        for keyword in keywords
        if len(keyword) >= 2
    ]


def keyword_search_chunks(question, limit=5, document_id=None):
    keywords = extract_keywords(question)
    scroll_filter = None

    if document_id is not None:
        scroll_filter = Filter(
            must=[
                FieldCondition(
                    key="document_id",
                    match=MatchValue(value=document_id),
                )
            ]
        )
    points, _ = qdrant_client.scroll(
        collection_name=COLLECTION_NAME,
        scroll_filter=scroll_filter,
        limit=1000,
        with_payload=True,
        with_vectors=False,
    )
    matched_sources = []
    for point in points:
        payload = point.payload
        text = payload.get("text", "")

        keyword_score = 0

        for keyword in keywords:
            if keyword.lower() in text.lower():
                keyword_score += 1

        if keyword_score > 0:
            source = {
                "document_id": payload["document_id"],
                "text": text,
                "filename": payload["filename"],
                "chunk_index": payload["chunk_index"],
                "page_number": payload["page_number"],
                "keyword_score": keyword_score,
            }

            matched_sources.append(source)

    matched_sources.sort(
        key=lambda source: source["keyword_score"],
        reverse=True,
    )

    return matched_sources[:limit]


def build_source_key(source):
    return source["document_id"], source["chunk_index"]


def hybrid_search_chunks(question, candidate_k=6, keyword_limit=6, document_id=None):
    vector_sources = search_chunks(question, candidate_k, document_id)
    keyword_sources = keyword_search_chunks(question, keyword_limit, document_id)

    merged_sources = {}

    for source in vector_sources:
        source_key = build_source_key(source)
        merged_sources[source_key] = source

    for source in keyword_sources:
        source_key = build_source_key(source)

        if source_key in merged_sources:
            merged_sources[source_key]["keyword_score"] = source["keyword_score"]
        else:
            merged_sources[source_key] = source

    return list(merged_sources.values())
