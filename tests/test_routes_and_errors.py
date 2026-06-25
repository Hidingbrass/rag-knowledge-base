"""API 路由和统一异常的回归测试。

这个文件主要验证 HTTP 层是否保持稳定：
- main.py 拆分后，原来的接口路径仍然存在。
- 全局异常处理器能把业务异常转换成统一 JSON 响应。

这些测试不调用真实大模型，也不连接真实 Qdrant。
它们只测试 FastAPI app 的路由注册和参数校验结果。
"""

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)


def test_original_routes_are_registered():
    """确认工程化拆分后，原有核心接口路径没有丢失。"""
    route_paths = {
        route.path
        for route in app.routes
        if hasattr(route, "path")
    }

    expected_paths = {
        "/health",
        "/qdrant/health",
        "/chat",
        "/documents/preview",
        "/documents/index",
        "/documents/{document_id}",
        "/documents",
        "/search/demo",
        "/embedding/test",
        "/rag/chat",
        "/rag/chat/rerank",
    }

    assert expected_paths.issubset(route_paths)


def test_rerank_top_k_greater_than_candidate_k_returns_bad_request():
    """rerank_top_k 不能大于 candidate_k，否则应该返回统一 400 错误。"""
    response = client.post(
        "/rag/chat/rerank",
        json={
            "question": "test question",
            "candidate_k": 2,
            "rerank_top_k": 3,
        },
    )

    assert response.status_code == 400
    assert response.json() == {
        "error_code": "BAD_REQUEST",
        "message": "rerank_top_k 不能大于 candidate_k",
    }


def test_preview_document_rejects_non_pdf_file():
    """上传非 PDF 文件时，documents API 应该返回统一 400 错误。"""
    response = client.post(
        "/documents/preview",
        files={
            "file": ("note.txt", b"hello", "text/plain")
        },
    )

    assert response.status_code == 400
    assert response.json() == {
        "error_code": "BAD_REQUEST",
        "message": "目前只支持 PDF 文件",
    }
