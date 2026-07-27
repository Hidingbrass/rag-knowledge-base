"""FastAPI 服务间 API Key 的回归测试。"""

from types import SimpleNamespace

from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.core import auth as auth_module


def create_test_client() -> TestClient:
    app = FastAPI(dependencies=[Depends(auth_module.verify_api_key)])

    @app.get("/health")
    def health():
        return {"status": "ok"}

    @app.get("/private")
    def private_route():
        return {"status": "ok"}

    return TestClient(app)


def test_health_remains_public_when_internal_api_key_is_enabled(monkeypatch):
    monkeypatch.setattr(
        auth_module,
        "settings",
        SimpleNamespace(fastapi_api_key="internal-test-key"),
    )

    response = create_test_client().get("/health")

    assert response.status_code == 200


def test_private_route_rejects_missing_or_invalid_internal_api_key(monkeypatch):
    monkeypatch.setattr(
        auth_module,
        "settings",
        SimpleNamespace(fastapi_api_key="internal-test-key"),
    )
    client = create_test_client()

    missing = client.get("/private")
    invalid = client.get("/private", headers={"X-API-Key": "wrong-key"})

    assert missing.status_code == 401
    assert invalid.status_code == 401
    assert missing.json()["detail"]["message"] == "API Key 无效或缺失"


def test_private_route_accepts_matching_internal_api_key(monkeypatch):
    monkeypatch.setattr(
        auth_module,
        "settings",
        SimpleNamespace(fastapi_api_key="internal-test-key"),
    )

    response = create_test_client().get(
        "/private",
        headers={"X-API-Key": "internal-test-key"},
    )

    assert response.status_code == 200


def test_openapi_declares_internal_api_key_security_scheme(monkeypatch):
    monkeypatch.setattr(
        auth_module,
        "settings",
        SimpleNamespace(fastapi_api_key="internal-test-key"),
    )
    client = create_test_client()

    schema = client.get("/openapi.json").json()

    security_schemes = schema["components"]["securitySchemes"]
    assert security_schemes["APIKeyHeader"] == {
        "type": "apiKey",
        "in": "header",
        "name": "X-API-Key",
    }
