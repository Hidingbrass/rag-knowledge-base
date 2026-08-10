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


def get_probability_env(name: str, default: float) -> float:
    """读取 0 到 1 之间的概率配置。"""
    value = get_float_env(name, default)
    if value < 0.0 or value > 1.0:
        raise ValueError(f"{name} 必须在 0 到 1 之间")
    return value


def get_bool_env(name: str, default: bool) -> bool:
    """读取布尔环境变量，并拒绝含义不明确的配置值。"""
    value = os.getenv(name)

    if value is None:
        return default

    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False

    raise ValueError(f"{name} 必须是 true/false、1/0、yes/no 或 on/off")


@dataclass(frozen=True)
class Settings:
    """项目运行配置。

    字段说明：
    - dashscope_api_key：通义千问 API Key。
    - dashscope_base_url：DashScope OpenAI-compatible API 地址。
    - qdrant_url：Qdrant 服务地址。
    - qdrant_collection_name：保存 RAG chunk 的 collection 名称。
    - qdrant_dense_vector_name / qdrant_sparse_vector_name：Qdrant 命名向量字段。
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
    qdrant_dense_vector_name: str
    qdrant_sparse_vector_name: str
    embedding_model: str
    embedding_dimensions: int
    chat_model: str
    intent_model: str
    intent_classifier_min_confidence: float
    rerank_model: str
    rerank_api_url: str
    request_timeout_seconds: float
    log_level: str
    default_top_k: int
    default_min_score: float
    default_candidate_k: int
    default_sparse_limit: int
    default_rerank_top_k: int
    rerank_document_diversity_enabled: bool
    default_rerank_min_score: float
    default_fallback_min_score: float
    slow_rerank_threshold: float
    max_upload_size_mb: int
    fastapi_api_key: str
    vision_model: str
    job_ocr_pdf_max_pages: int
    chat_max_output_tokens: int
    structured_chat_max_output_tokens: int
    max_question_chars: int
    max_job_text_chars: int
    model_retry_max_attempts: int
    model_retry_backoff_seconds: float
    model_circuit_failure_threshold: int
    model_circuit_reset_seconds: float
    chat_input_price_per_million_yuan: float
    chat_output_price_per_million_yuan: float
    embedding_price_per_million_yuan: float
    rerank_price_per_million_yuan: float


settings = Settings(
    dashscope_api_key=get_required_env("DASHSCOPE_API_KEY"),
    dashscope_base_url=os.getenv(
        "DASHSCOPE_BASE_URL",
        "https://dashscope.aliyuncs.com/compatible-mode/v1",
    ),
    qdrant_url=os.getenv("QDRANT_URL", "http://127.0.0.1:6333"),
    qdrant_collection_name=os.getenv("QDRANT_COLLECTION_NAME", "rag_chunks_hybrid_v1"),
    qdrant_dense_vector_name=os.getenv("QDRANT_DENSE_VECTOR_NAME", "dense"),
    qdrant_sparse_vector_name=os.getenv("QDRANT_SPARSE_VECTOR_NAME", "sparse"),
    embedding_model=os.getenv("QWEN_EMBEDDING_MODEL", "text-embedding-v4"),
    embedding_dimensions=get_int_env("QWEN_EMBEDDING_DIMENSIONS", 1024),
    chat_model=os.getenv("QWEN_CHAT_MODEL", "qwen-plus"),
    intent_model=os.getenv("QWEN_INTENT_MODEL", "qwen-flash"),
    intent_classifier_min_confidence=get_probability_env(
        "INTENT_CLASSIFIER_MIN_CONFIDENCE",
        0.80,
    ),
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
    default_sparse_limit=get_int_env("RAG_DEFAULT_SPARSE_LIMIT", 6),
    default_rerank_top_k=get_int_env("RAG_DEFAULT_RERANK_TOP_K", 3),
    rerank_document_diversity_enabled=get_bool_env(
        "RAG_RERANK_DOCUMENT_DIVERSITY_ENABLED",
        True,
    ),
    default_rerank_min_score=get_float_env("RAG_DEFAULT_RERANK_MIN_SCORE", 0.75),
    default_fallback_min_score=get_float_env("RAG_DEFAULT_FALLBACK_MIN_SCORE", 0.55),
    slow_rerank_threshold=get_float_env("RAG_SLOW_RERANK_THRESHOLD", 3.0),
    max_upload_size_mb=get_int_env("MAX_UPLOAD_SIZE_MB", 10),
    fastapi_api_key=os.getenv("FASTAPI_API_KEY", ""),
    vision_model=os.getenv("QWEN_VISION_MODEL", "qwen-vl-plus"),
    job_ocr_pdf_max_pages=get_int_env("JOB_OCR_PDF_MAX_PAGES", 3),
    chat_max_output_tokens=get_int_env("QWEN_CHAT_MAX_OUTPUT_TOKENS", 2048),
    structured_chat_max_output_tokens=get_int_env(
        "QWEN_STRUCTURED_MAX_OUTPUT_TOKENS",
        4096,
    ),
    max_question_chars=get_int_env("AI_MAX_QUESTION_CHARS", 2000),
    max_job_text_chars=get_int_env("AI_MAX_JOB_TEXT_CHARS", 30000),
    model_retry_max_attempts=get_int_env("MODEL_RETRY_MAX_ATTEMPTS", 3),
    model_retry_backoff_seconds=get_float_env("MODEL_RETRY_BACKOFF_SECONDS", 0.25),
    model_circuit_failure_threshold=get_int_env("MODEL_CIRCUIT_FAILURE_THRESHOLD", 5),
    model_circuit_reset_seconds=get_float_env("MODEL_CIRCUIT_RESET_SECONDS", 30.0),
    chat_input_price_per_million_yuan=get_float_env(
        "MODEL_CHAT_INPUT_PRICE_PER_MILLION_YUAN",
        0.0,
    ),
    chat_output_price_per_million_yuan=get_float_env(
        "MODEL_CHAT_OUTPUT_PRICE_PER_MILLION_YUAN",
        0.0,
    ),
    embedding_price_per_million_yuan=get_float_env(
        "MODEL_EMBEDDING_PRICE_PER_MILLION_YUAN",
        0.0,
    ),
    rerank_price_per_million_yuan=get_float_env(
        "MODEL_RERANK_PRICE_PER_MILLION_YUAN",
        0.0,
    ),
)
