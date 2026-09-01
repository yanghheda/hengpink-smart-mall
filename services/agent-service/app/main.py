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
from datetime import UTC, datetime
from hashlib import sha256
from typing import Any, Protocol
from urllib.error import URLError
from urllib.request import ProxyHandler, build_opener
from urllib.request import Request as UrlRequest

from fastapi import FastAPI, Request, Response, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from app.graph.state import InitialGraphState
from app.graph.workflow import StubGraphModel, build_shopping_decision_graph
from app.tools.client import CommerceToolClient, UrlLibToolTransport

logger = logging.getLogger("hengpick.agent")
INTERNAL_HTTP_OPENER = build_opener(ProxyHandler({}))
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
    tool_api_token: str | None = None

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
            tool_api_token=os.getenv("COMMERCE_INTERNAL_SERVICE_TOKEN"),
        )


class ReadinessProbe(Protocol):
    async def checks(self) -> dict[str, str]: ...


class AgentRunCallback(BaseModel):
    """回调地址只使用已配置标识，禁止请求携带任意 URL。"""

    model_config = ConfigDict(extra="forbid")
    baseUrlId: str = Field(pattern=r"^[a-z0-9-]{1,64}$")
    callbackToken: str = Field(min_length=1, max_length=4096)


class AgentRunBudget(BaseModel):
    """Run 的超时与模型调用预算。"""

    model_config = ConfigDict(extra="forbid")
    softTimeoutMs: int = Field(ge=100, le=60_000)
    hardTimeoutMs: int = Field(ge=100, le=120_000)
    maxModelCalls: int = Field(ge=0, le=10)


class AgentRunRequest(BaseModel):
    """Java 提交给 Python 的异步 Run 协议。"""

    model_config = ConfigDict(extra="forbid")
    runId: str = Field(min_length=1, max_length=64)
    sessionId: str = Field(min_length=1, max_length=64)
    runVersion: int = Field(ge=1)
    versions: dict[str, str]
    input: dict[str, Any]
    callback: AgentRunCallback
    budget: AgentRunBudget


class RunExecutor(Protocol):
    async def execute(self, request: AgentRunRequest) -> None: ...


class CallbackSender(Protocol):
    async def post(self, path: str, token: str, body: dict[str, Any]) -> None: ...


class HttpCallbackSender:
    """只向配置的 Commerce API 地址发送回调，忽略请求中的地址文本。"""

    def __init__(self, base_url: str) -> None:
        self._base_url = base_url.rstrip("/")

    async def post(self, path: str, token: str, body: dict[str, Any]) -> None:
        await asyncio.to_thread(self._post_blocking, path, token, body)

    def _post_blocking(self, path: str, token: str, body: dict[str, Any]) -> None:
        payload = json.dumps(body, separators=(",", ":"), sort_keys=True).encode("utf-8")
        request = UrlRequest(
            f"{self._base_url}{path}",
            data=payload,
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
            method="POST",
        )
        with INTERNAL_HTTP_OPENER.open(request, timeout=2) as response:
            if not 200 <= response.status < 300:
                raise RuntimeError(f"回调返回非成功状态: {response.status}")


class StubRunExecutor:
    """P09-S02 的确定性 Stub；真实 Graph 留到 P10。"""

    def __init__(self, callback_sender: CallbackSender | None = None) -> None:
        self.started_run_ids: list[str] = []
        self._callback_sender = callback_sender

    async def execute(self, request: AgentRunRequest) -> None:
        self.started_run_ids.append(request.runId)
        if self._callback_sender is None:
            await asyncio.sleep(0)
            return
        now = datetime.now(UTC).isoformat().replace("+00:00", "Z")
        step_without_hash: dict[str, Any] = {
            "runVersion": request.runVersion,
            "sequence": 1,
            "node": "STUB",
            "status": "COMPLETED",
            "startedAt": now,
            "completedAt": now,
            "inputSummary": {"messageCount": len(request.input.get("messages", []))},
            "outputSummary": {"stub": True},
        }
        step = {**step_without_hash, "contentHash": callback_body_hash(step_without_hash)}
        await self._callback_sender.post(
            f"/internal/v1/decision-runs/{request.runId}/steps",
            request.callback.callbackToken,
            step,
        )
        completion_without_hash: dict[str, Any] = {
            "runVersion": request.runVersion,
            "completionType": "REPORT_READY",
            "resultSummary": {"generationType": "STUB", "message": "Stub Run 已完成"},
            "completedAt": now,
        }
        completion = {
            **completion_without_hash,
            "contentHash": callback_body_hash(completion_without_hash),
        }
        await self._callback_sender.post(
            f"/internal/v1/decision-runs/{request.runId}/complete",
            request.callback.callbackToken,
            completion,
        )


class GraphRunExecutor:
    """把异步 Run 请求接入真实 Commerce Tool 商品主链。"""

    def __init__(
        self,
        settings: AgentSettings,
        callback_sender: CallbackSender,
    ) -> None:
        self._settings = settings
        self._callback_sender = callback_sender

    async def execute(self, request: AgentRunRequest) -> None:
        client = CommerceToolClient(
            UrlLibToolTransport(
                self._settings.tool_api_base_url,
                self._settings.tool_api_token,
            )
        )
        state = InitialGraphState(
            run_id=request.runId,
            session_id=request.sessionId,
            run_version=request.runVersion,
            user_id_ref=str(request.input.get("userIdRef", "internal-user-ref")),
            dataset_version=request.versions["dataset"],
            prompt_version=request.versions.get("prompt", "intent-v1"),
            scoring_version=request.versions.get("scoring", "scoring-v1"),
            pricing_rule_version=request.versions.get("pricing", "pricing-v1"),
            messages=request.input.get("messages", []),
            budget={"max_model_calls": request.budget.maxModelCalls},
            previous_intent=request.input.get("previousIntent"),
            clarification_round=int(request.input.get("clarificationRound", 0)),
        ).to_graph_state()
        graph = build_shopping_decision_graph(StubGraphModel(), client)
        result = await asyncio.to_thread(graph.invoke, state)
        now = datetime.now(UTC).isoformat().replace("+00:00", "Z")
        step_without_hash: dict[str, Any] = {
            "runVersion": request.runVersion,
            "sequence": 1,
            "node": "PRODUCT_MAIN_CHAIN",
            "status": "COMPLETED",
            "startedAt": now,
            "completedAt": now,
            "inputSummary": {"messageCount": len(request.input.get("messages", []))},
            "outputSummary": {
                "candidateCount": len(result["candidates"]),
                "toolCallCount": len(result["tool_calls"]),
            },
        }
        await self._callback_sender.post(
            f"/internal/v1/decision-runs/{request.runId}/steps",
            request.callback.callbackToken,
            {**step_without_hash, "contentHash": callback_body_hash(step_without_hash)},
        )
        completion_type = (
            "CLARIFICATION_REQUIRED"
            if (result.get("clarification") or {}).get("questions")
            else ("NO_RESULT" if not result["candidates"] else "REPORT_READY")
        )
        completion_without_hash: dict[str, Any] = {
            "runVersion": request.runVersion,
            "completionType": completion_type,
            "resultSummary": {
                "generationType": "TOOL_BACKED_REVIEW_STUB",
                "candidates": result["candidates"][:3],
                "scoreCards": result["score_cards"][:3],
                "pricePlans": result["price_plans"],
                "versions": request.versions,
                "warnings": result["warnings"],
            },
            "completedAt": now,
        }
        await self._callback_sender.post(
            f"/internal/v1/decision-runs/{request.runId}/complete",
            request.callback.callbackToken,
            {
                **completion_without_hash,
                "contentHash": callback_body_hash(completion_without_hash),
            },
        )


def callback_body_hash(body: dict[str, Any]) -> str:
    """对不含 contentHash 的回调体计算稳定 SHA-256。"""

    payload = json.dumps(body, separators=(",", ":"), sort_keys=True)
    return sha256(payload.encode("utf-8")).hexdigest()


def canonical_request_hash(request: AgentRunRequest) -> str:
    """对规范化请求计算哈希，用于识别同 runId 的内容冲突。"""

    payload = request.model_dump_json(by_alias=True, exclude_none=True)
    return sha256(payload.encode("utf-8")).hexdigest()


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
            with INTERNAL_HTTP_OPENER.open(url, timeout=0.5) as response:
                return 200 <= response.status < 300
        except (OSError, URLError):
            return False


def create_app(
    settings: AgentSettings | None = None,
    readiness_probe: ReadinessProbe | None = None,
    run_executor: RunExecutor | None = None,
) -> FastAPI:
    @asynccontextmanager
    async def lifespan(application: FastAPI) -> AsyncIterator[None]:
        configured = settings or AgentSettings.from_environment()
        application.state.settings = configured
        application.state.readiness_probe = readiness_probe or HttpReadinessProbe(configured)
        application.state.run_executor = run_executor or GraphRunExecutor(
            configured, HttpCallbackSender(configured.tool_api_base_url)
        )
        application.state.run_registry = {}
        yield

    application = FastAPI(title="HengPick Agent Service", version="0.1.0", lifespan=lifespan)
    if settings is not None:
        application.state.settings = settings
        application.state.readiness_probe = readiness_probe or HttpReadinessProbe(settings)
        application.state.run_executor = run_executor or GraphRunExecutor(
            settings, HttpCallbackSender(settings.tool_api_base_url)
        )
        application.state.run_registry = {}

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

    @application.post("/internal/v1/agent-runs", status_code=status.HTTP_202_ACCEPTED)
    async def start_agent_run(request: Request, body: AgentRunRequest) -> JSONResponse:
        request_hash = canonical_request_hash(body)
        registry: dict[str, dict[str, str]] = request.app.state.run_registry
        existing = registry.get(body.runId)
        if existing is not None:
            if existing["requestHash"] != request_hash:
                return JSONResponse(
                    status_code=status.HTTP_409_CONFLICT,
                    content={
                        "error": {
                            "code": "AGENT_RUN_PAYLOAD_CONFLICT",
                            "message": "同一 runId 对应的请求内容不一致",
                            "retryable": False,
                        }
                    },
                )
            return JSONResponse(
                status_code=status.HTTP_202_ACCEPTED,
                content={"runId": body.runId, "status": existing["status"]},
            )

        registry[body.runId] = {"requestHash": request_hash, "status": "ACCEPTED"}

        async def execute_stub() -> None:
            registry[body.runId]["status"] = "RUNNING"
            try:
                await request.app.state.run_executor.execute(body)
                registry[body.runId]["status"] = "COMPLETED"
            except Exception:
                registry[body.runId]["status"] = "FAILED"
                logger.exception("Stub Run 执行失败 runId=%s", body.runId)
                completed_at = datetime.now(UTC).isoformat().replace("+00:00", "Z")
                failure_without_hash: dict[str, Any] = {
                    "runVersion": body.runVersion,
                    "completionType": "FAILED",
                    "resultSummary": {
                        "failureCode": "AGENT_EXECUTION_FAILED",
                        "warnings": [],
                        "versions": body.versions,
                    },
                    "completedAt": completed_at,
                }
                try:
                    await HttpCallbackSender(request.app.state.settings.tool_api_base_url).post(
                        f"/internal/v1/decision-runs/{body.runId}/complete",
                        body.callback.callbackToken,
                        {
                            **failure_without_hash,
                            "contentHash": callback_body_hash(failure_without_hash),
                        },
                    )
                except Exception:
                    logger.exception("失败终态回调失败 runId=%s", body.runId)

        asyncio.create_task(execute_stub())
        return JSONResponse(
            status_code=status.HTTP_202_ACCEPTED,
            content={"runId": body.runId, "status": "ACCEPTED"},
        )

    return application


app = create_app()


def configured_port() -> int:
    """暴露环境变量驱动的端口，便于启动器做可重复测试。"""
    return int(os.getenv("AGENT_PORT", "8000"))
