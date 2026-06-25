"""搜索演示路由。

这里保留学习项目中的两个演示接口：
- /search/demo
- /embedding/test
"""

from fastapi import APIRouter

from app.schemas.search import SearchRequest
from app.services.search_service import embedding_test_response, search_demo_response


router = APIRouter()


@router.post("/search/demo")
def search_demo(request: SearchRequest):
    """本地向量相似度演示接口。

    注意：这里返回 score，保持和 RAG source 的 vector_score 区分。
    """
    return search_demo_response(request)


@router.post("/embedding/test")
def embedding_test(request: SearchRequest):
    """单条文本 Embedding 测试接口。"""
    return embedding_test_response(request)
