import asyncio
import json
import logging
import os
import re
import time
import uuid
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from typing import Protocol
from urllib.error import URLError
from urllib.request import urlopen

from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse

logger = logging.getLogger("hengpick.agent")
TRACEPARENT = re.compile(r"00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
REQUEST_ID = re.compile(r"[A-Za-z0-9_-]{1,128}")


class ConfigurationError(ValueError):
    """Raised before serving traffic when required service configuration is absent."""


@dataclass(frozen=True)
class AgentSettings:
    environment: str
    tool_api_base_url: str
    qdrant_url: str
    model_provider: str
    model_name: str
    model_api_key: str | None

    @classmethod
    def from_environment(cls) -> "AgentSettings":
        required = {
            "APP_ENV": os.getenv("APP_ENV"),
            "AGENT_TOOL_API_BASE_URL": os.getenv("AGENT_TOOL_API_BASE_URL"),
            "QDRANT_URL": os.getenv("QDRANT_URL"),
            "AGENT_MODEL_PROVIDER": os.getenv("AGENT_MODEL_PROVIDER"),
            "AGENT_MODEL_NAME": os.getenv("AGENT_MODEL_NAME"),
        }
        missing = [name for name, value in required.items() if not value or not value.strip()]
        if missing:
            raise ConfigurationError(f"Missing required configuration: {', '.join(missing)}")

        provider = required["AGENT_MODEL_PROVIDER"]
        api_key = os.getenv("AGENT_MODEL_API_KEY")
        if provider != "stub" and not api_key:
            raise ConfigurationError("Missing required configuration: AGENT_MODEL_API_KEY")
        return cls(
            environment=required["APP_ENV"],
            tool_api_base_url=required["AGENT_TOOL_API_BASE_URL"],
            qdrant_url=required["QDRANT_URL"],
            model_provider=provider,
            model_name=required["AGENT_MODEL_NAME"],
            model_api_key=api_key,
        )


class ReadinessProbe(Protocol):
    async def checks(self) -> dict[str, str]: ...


@dataclass(frozen=True)
class StaticReadinessProbe:
    qdrant_up: bool
    tool_api_up: bool

    async def checks(self) -> dict[str, str]:
        return {
            "modelConfiguration": "UP",
            "qdrant": "UP" if self.qdrant_up else "DOWN",
            "toolApi": "UP" if self.tool_api_up else "DOWN",
        }


class HttpReadinessProbe:
    def __init__(self, settings: AgentSettings) -> None:
        self._settings = settings

    async def checks(self) -> dict[str, str]:
        qdrant_url = f"{self._settings.qdrant_url.rstrip('/')}/readyz"
        tool_api_url = f"{self._settings.tool_api_base_url.rstrip('/')}/actuator/health/liveness"
        qdrant_up, tool_api_up = await asyncio.gather(
            asyncio.to_thread(self._is_up, qdrant_url),
            asyncio.to_thread(self._is_up, tool_api_url),
        )
        return {
            "modelConfiguration": "UP",
            "qdrant": "UP" if qdrant_up else "DOWN",
            "toolApi": "UP" if tool_api_up else "DOWN",
        }

    @staticmethod
    def _is_up(url: str) -> bool:
        try:
            with urlopen(url, timeout=0.5) as response:
                return 200 <= response.status < 300
        except (OSError, URLError):
            return False


def create_app(
    settings: AgentSettings | None = None, readiness_probe: ReadinessProbe | None = None
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        configured = settings or AgentSettings.from_environment()
        application.state.settings = configured
        application.state.readiness_probe = readiness_probe or HttpReadinessProbe(configured)
        yield

    application = FastAPI(title="HengPick Agent Service", version="0.1.0", lifespan=lifespan)
    if settings is not None:
        application.state.settings = settings
        application.state.readiness_probe = readiness_probe or HttpReadinessProbe(settings)

    @application.middleware("http")
    async def correlate_request(request: Request, call_next) -> Response:
        request_id = request.headers.get("X-Request-Id", "")
        if not REQUEST_ID.fullmatch(request_id):
            request_id = uuid.uuid4().hex
        traceparent = request.headers.get("traceparent", "")
        if not TRACEPARENT.fullmatch(traceparent):
            traceparent = f"00-{uuid.uuid4().hex}-{uuid.uuid4().hex[:16]}-01"
        started_at = time.perf_counter()
        response = await call_next(request)
        response.headers["X-Request-Id"] = request_id
        response.headers["traceparent"] = traceparent
        logger.info(
            json.dumps(
                {
                    "service": "agent-service",
                    "environment": getattr(
                        getattr(request.app.state, "settings", None), "environment", "unconfigured"
                    ),
                    "event": "request.completed",
                    "durationMs": round((time.perf_counter() - started_at) * 1000),
                    "result": response.status_code,
                    "requestId": request_id,
                    "traceparent": traceparent,
                }
            )
        )
        return response

    @application.get("/")
    async def landing() -> dict[str, str]:
        return {"service": "agent-service", "status": "UP", "scope": "P01-S01"}

    @application.get("/health/live")
    async def live() -> dict[str, str]:
        return {"status": "UP"}

    @application.get("/health/ready")
    async def ready(request: Request) -> JSONResponse:
        checks = await request.app.state.readiness_probe.checks()
        status = "UP" if all(value == "UP" for value in checks.values()) else "DOWN"
        return JSONResponse(
            status_code=200 if status == "UP" else 503,
            content={"status": status, "checks": checks},
        )

    return application


app = create_app()


def configured_port() -> int:
    """Expose the environment-driven port for repeatable launcher tests."""
    return int(os.getenv("AGENT_PORT", "8000"))
