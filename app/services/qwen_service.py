"""通义千问模型调用服务。

这个文件集中封装 DashScope OpenAI-compatible API 的调用：
- create_embedding：单条文本向量化。
- create_embeddings：批量文本向量化。
- chat_completion：调用 qwen-plus 生成回答。
- chat_completion_with_images：调用视觉模型识别截图或扫描 PDF。

把模型调用放在 service 层，可以避免 main.py 直接依赖模型客户端细节。
"""

import base64

from openai import OpenAI

from app.core.config import settings


# client 是 OpenAI-compatible 客户端，实际请求会发送到 DashScope。
client = OpenAI(
    api_key=settings.dashscope_api_key,
    base_url=settings.dashscope_base_url,
    timeout=settings.request_timeout_seconds,
)


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
    response = client.embeddings.create(
        model=settings.embedding_model,
        input=text,
        dimensions=settings.embedding_dimensions,
        encoding_format="float",
    )

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
    response = client.embeddings.create(
        model=settings.embedding_model,
        input=texts,
        dimensions=settings.embedding_dimensions,
        encoding_format="float",
    )

    return [item.embedding for item in response.data]


def chat_completion(messages: list[dict]) -> str:
    """调用 qwen-plus 生成聊天回答。

    参数：
    - messages：OpenAI-compatible chat messages。

    返回：
    - 模型生成的文本答案。

    使用位置：
    - /chat
    - rag_service 中的普通 RAG 和 Rerank RAG。
    """
    completion = client.chat.completions.create(
        model=settings.chat_model,
        messages=messages,
    )

    return completion.choices[0].message.content


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

    completion = client.chat.completions.create(
        model=settings.vision_model,
        messages=[
            {
                "role": "user",
                "content": content,
            }
        ],
    )

    return completion.choices[0].message.content
