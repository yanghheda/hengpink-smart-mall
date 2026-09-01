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
from urllib.error import HTTPError

from fastapi import FastAPI, Request, Response, status
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from app.graph.state import InitialGraphState
from app.graph.workflow import StubGraphModel, build_shopping_decision_graph
from app.model.bailian import BailianModel
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
    model_base_url: str | None = None
    model_reasoning_effort: str = "low"
    model_timeout_seconds: float = 60
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
        if provider not in {"stub", "aliyun_bailian"}:
            raise ConfigurationError("AGENT_MODEL_PROVIDER must be one of: stub, aliyun_bailian")
        api_key = os.getenv("AGENT_MODEL_API_KEY")
        if provider != "stub" and not api_key:
            raise ConfigurationError("Missing required configuration: AGENT_MODEL_API_KEY")
        base_url = os.getenv("AGENT_MODEL_BASE_URL")
        if provider != "stub" and not base_url:
            raise ConfigurationError("Missing required configuration: AGENT_MODEL_BASE_URL")
        reasoning_effort = os.getenv("AGENT_MODEL_REASONING_EFFORT", "low")
        if reasoning_effort not in {"low", "medium", "xhigh"}:
            raise ConfigurationError(
                "AGENT_MODEL_REASONING_EFFORT must be one of: low, medium, xhigh"
            )
        try:
            model_timeout_seconds = float(os.getenv("AGENT_MODEL_TIMEOUT_SECONDS", "60"))
        except ValueError as error:
            raise ConfigurationError("AGENT_MODEL_TIMEOUT_SECONDS must be a number") from error
        if not 5 <= model_timeout_seconds <= 120:
            raise ConfigurationError("AGENT_MODEL_TIMEOUT_SECONDS must be between 5 and 120")
        return cls(
            environment=required["APP_ENV"],
            tool_api_base_url=required["AGENT_TOOL_API_BASE_URL"],
            qdrant_url=required["QDRANT_URL"],
            model_provider=provider,
            model_name=required["AGENT_MODEL_NAME"],
            model_api_key=api_key,
            model_base_url=base_url,
            model_reasoning_effort=reasoning_effort,
            model_timeout_seconds=model_timeout_seconds,
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
        try:
            with INTERNAL_HTTP_OPENER.open(request, timeout=5) as response:
                if not 200 <= response.status < 300:
                    raise RuntimeError(f"回调返回非成功状态: {response.status}")
        except HTTPError as error:
            response_body = error.read().decode("utf-8", errors="replace")[:1000]
            raise RuntimeError(
                f"回调返回非成功状态: {error.code}, body={response_body}"
            ) from error


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
        model = (
            StubGraphModel()
            if self._settings.model_provider == "stub"
            else BailianModel(
                api_key=self._settings.model_api_key or "",
                base_url=self._settings.model_base_url or "",
                model_name=self._settings.model_name,
                reasoning_effort=self._settings.model_reasoning_effort,
                timeout_seconds=self._settings.model_timeout_seconds,
            )
        )
        graph = build_shopping_decision_graph(model, client)
        graph_started_at = datetime.now(UTC)
        result = await asyncio.to_thread(graph.invoke, state)
        graph_completed_at = datetime.now(UTC)
        now = graph_completed_at.isoformat().replace("+00:00", "Z")
        for sequence, trace_step in enumerate(
            graph_trace_steps(result, request, graph_started_at, graph_completed_at), start=1
        ):
            step_without_hash = {"runVersion": request.runVersion, "sequence": sequence, **trace_step}
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
        result_summary = graph_result_summary(
            result, {**request.versions, "model": self._settings.model_name}
        )
        completion_without_hash: dict[str, Any] = {
            "runVersion": request.runVersion,
            "completionType": completion_type,
            "resultSummary": result_summary,
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


def graph_result_summary(result: dict[str, Any], versions: dict[str, str]) -> dict[str, Any]:
    ranked_sku_ids = {
        str(card.get("skuId")) for card in result["score_cards"][:3] if card.get("skuId")
    }
    ranked_candidates = [
        candidate
        for candidate in result["candidates"]
        if str(candidate.get("skuId")) in ranked_sku_ids
    ]
    summary: dict[str, Any] = {
        "generationType": "TOOL_BACKED_REVIEW_STUB",
        "candidates": ranked_candidates,
        "scoreCards": result["score_cards"][:3],
        "pricePlans": result["price_plans"],
        "evidence": result.get("evidence", {}),
        "versions": versions,
        "warnings": result["warnings"],
    }
    if result.get("report") is not None:
        summary["reportNarrative"] = result["report"]
    if result.get("clarification") is not None:
        summary["clarification"] = result["clarification"]
    return summary


def graph_trace_steps(
    result: dict[str, Any], request: AgentRunRequest, started_at: datetime, completed_at: datetime
) -> list[dict[str, Any]]:
    """把公开 Graph 节点和 Tool 信封摘要投影为可观察调用链。"""

    tools_by_node = {
        "product": {"search_products", "get_product_specs"},
        "price": {"get_price_offers", "calculate_final_price"},
        "score": {"score_candidates"},
    }
    node_labels = {
        "load_context": "LOAD_CONTEXT", "intent": "INTENT_PARSE",
        "clarification": "CLARIFICATION", "product": "PRODUCT_SEARCH",
        "review_stub": "EVIDENCE_REVIEW", "price": "PRICE_CALCULATION",
        "score": "CANDIDATE_SCORING", "report_stub": "REPORT_GENERATION",
        "validate": "REPORT_VALIDATION", "no_result": "NO_RESULT",
    }
    started = started_at.isoformat().replace("+00:00", "Z")
    completed = completed_at.isoformat().replace("+00:00", "Z")
    traces = list(result.get("tool_calls", []))
    steps: list[dict[str, Any]] = []
    for node in result.get("completed_nodes", []):
        output: dict[str, Any] = {"status": "COMPLETED"}
        if node == "intent": output["categoryId"] = (result.get("intent") or {}).get("category")
        if node == "clarification": output["questionCount"] = len((result.get("clarification") or {}).get("questions", []))
        if node == "product": output["candidateCount"] = len(result.get("candidates", []))
        if node == "report_stub": output["generationType"] = (result.get("report") or {}).get("generation_type", "MODEL")
        steps.append({
            "node": node_labels.get(node, node.upper()), "status": "COMPLETED",
            "startedAt": started, "completedAt": completed,
            "inputSummary": {"messageCount": len(request.input.get("messages", []))} if node == "intent" else {},
            "outputSummary": output,
        })
        for tool in [item for item in traces if item.get("tool_name") in tools_by_node.get(node, set())]:
            tool_output = {"status": tool.get("status")}
            if tool.get("source_version") is not None:
                tool_output["sourceVersion"] = tool.get("source_version")
            if tool.get("error_code") is not None:
                tool_output["errorCode"] = tool.get("error_code")
            steps.append({
                "node": f"TOOL:{tool.get('tool_name')}",
                "status": "COMPLETED" if tool.get("status") == "SUCCESS" else "FAILED",
                "startedAt": started, "completedAt": completed,
                "inputSummary": {"toolName": tool.get("tool_name"), "toolCallId": tool.get("tool_call_id")},
                "outputSummary": tool_output,
            })
    return steps


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
