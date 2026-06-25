"""项目配置中心。

这个文件相当于 Spring Boot 中的 application.yml + ConfigurationProperties。

配置来源：
- 敏感信息优先放在 .env，例如 DASHSCOPE_API_KEY。
- 有默认值的工程参数在 Settings 中集中定义。

为什么要集中配置：
- 避免模型名称、Qdrant 地址、阈值散落在多个文件。
- 后续调参时只需要优先检查这个文件。
- 测试时也更容易覆盖配置。
"""

import os
from dataclasses import dataclass

from dotenv import load_dotenv


load_dotenv()


def get_required_env(name: str) -> str:
    """读取必填环境变量。

    参数：
    - name：环境变量名称。

    返回：
    - 环境变量值。

    抛出：
    - RuntimeError：当环境变量不存在或为空字符串时抛出。
    """
    value = os.getenv(name)

    if not value:
        raise RuntimeError(f"未找到 {name}，请检查 .env 文件")

    return value


def get_int_env(name: str, default: int) -> int:
    """读取整数环境变量。

    如果环境变量不存在，就使用 default。
    """
    value = os.getenv(name)

    if value is None:
        return default

    return int(value)


def get_float_env(name: str, default: float) -> float:
    """读取浮点数环境变量。

    如果环境变量不存在，就使用 default。
    """
    value = os.getenv(name)

    if value is None:
        return default

    return float(value)


@dataclass(frozen=True)
class Settings:
    """项目运行配置。

    字段说明：
    - dashscope_api_key：通义千问 API Key。
    - dashscope_base_url：DashScope OpenAI-compatible API 地址。
    - qdrant_url：Qdrant 服务地址。
    - qdrant_collection_name：保存 RAG chunk 的 collection 名称。
    - embedding_model：Embedding 模型名称。
    - embedding_dimensions：Embedding 向量维度。
    - chat_model：普通聊天和 RAG 生成使用的模型。
    - rerank_model：Rerank 使用的模型。
    - rerank_api_url：DashScope Rerank API 地址。
    - request_timeout_seconds：外部模型接口请求超时时间。
    - log_level：日志级别，例如 INFO、DEBUG、WARNING。
    - default_*：接口请求模型和评测脚本使用的默认参数。
    - slow_rerank_threshold：评测时判断 Rerank 调用偏慢的秒数阈值。
    """

    dashscope_api_key: str
    dashscope_base_url: str
    qdrant_url: str
    qdrant_collection_name: str
    embedding_model: str
    embedding_dimensions: int
    chat_model: str
    rerank_model: str
    rerank_api_url: str
    request_timeout_seconds: float
    log_level: str
    default_top_k: int
    default_min_score: float
    default_candidate_k: int
    default_rerank_top_k: int
    default_rerank_min_score: float
    default_fallback_min_score: float
    slow_rerank_threshold: float


settings = Settings(
    dashscope_api_key=get_required_env("DASHSCOPE_API_KEY"),
    dashscope_base_url=os.getenv(
        "DASHSCOPE_BASE_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
    ),
    qdrant_url=os.getenv("QDRANT_URL", "http://127.0.0.1:6333"),
    qdrant_collection_name=os.getenv("QDRANT_COLLECTION_NAME", "rag_chunks"),
    embedding_model=os.getenv("QWEN_EMBEDDING_MODEL", "text-embedding-v4"),
    embedding_dimensions=get_int_env("QWEN_EMBEDDING_DIMENSIONS", 1024),
    chat_model=os.getenv("QWEN_CHAT_MODEL", "qwen-plus"),
    rerank_model=os.getenv("QWEN_RERANK_MODEL", "qwen3-rerank"),
    rerank_api_url=os.getenv(
        "QWEN_RERANK_API_URL",
        "https://dashscope.aliyuncs.com/compatible-api/v1/reranks",
    ),
    request_timeout_seconds=get_float_env("MODEL_REQUEST_TIMEOUT_SECONDS", 60.0),
    log_level=os.getenv("LOG_LEVEL", "INFO"),
    default_top_k=get_int_env("RAG_DEFAULT_TOP_K", 3),
    default_min_score=get_float_env("RAG_DEFAULT_MIN_SCORE", 0.55),
    default_candidate_k=get_int_env("RAG_DEFAULT_CANDIDATE_K", 6),
    default_rerank_top_k=get_int_env("RAG_DEFAULT_RERANK_TOP_K", 3),
    default_rerank_min_score=get_float_env("RAG_DEFAULT_RERANK_MIN_SCORE", 0.75),
    default_fallback_min_score=get_float_env("RAG_DEFAULT_FALLBACK_MIN_SCORE", 0.55),
    slow_rerank_threshold=get_float_env("RAG_SLOW_RERANK_THRESHOLD", 3.0),
)
