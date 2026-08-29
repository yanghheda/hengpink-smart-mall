import pytest
from fastapi.testclient import TestClient

from app.main import (
    AgentSettings,
    ConfigurationError,
    StaticReadinessProbe,
    app,
    configured_port,
    create_app,
)

client = TestClient(app)


def test_live_health_is_visible() -> None:
    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_ready_health_reports_missing_dependencies() -> None:
    settings = AgentSettings(
        environment="test",
        tool_api_base_url="http://commerce-api:8080",
        qdrant_url="http://qdrant:6333",
        model_provider="stub",
        model_name="stub-model",
        model_api_key=None,
        tool_api_token="test-internal-token",
    )
    response = TestClient(
        create_app(
            settings=settings,
            readiness_probe=StaticReadinessProbe(qdrant_up=False, tool_api_up=False),
        )
    ).get("/health/ready")

    assert response.status_code == 503
    assert response.json()["status"] == "DOWN"
    assert response.json()["checks"] == {
        "modelConfiguration": "UP",
        "qdrant": "DOWN",
        "toolApi": "DOWN",
    }


def test_landing_declares_minimal_scope() -> None:
    response = client.get("/")

    assert response.status_code == 200
    assert response.json()["scope"] == "P01-S01"


def test_agent_port_comes_from_environment(monkeypatch) -> None:
    monkeypatch.setenv("AGENT_PORT", "18000")

    assert configured_port() == 18000


def test_missing_required_agent_configuration_fails_fast(monkeypatch) -> None:
    monkeypatch.delenv("AGENT_TOOL_API_BASE_URL", raising=False)

    with pytest.raises(ConfigurationError, match="AGENT_TOOL_API_BASE_URL"):
        AgentSettings.from_environment()
