from fastapi.testclient import TestClient

from app.main import app, configured_port

client = TestClient(app)


def test_live_health_is_visible() -> None:
    response = client.get("/health/live")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_landing_declares_minimal_scope() -> None:
    response = client.get("/")

    assert response.status_code == 200
    assert response.json()["scope"] == "P01-S01"


def test_agent_port_comes_from_environment(monkeypatch) -> None:
    monkeypatch.setenv("AGENT_PORT", "18000")

    assert configured_port() == 18000
