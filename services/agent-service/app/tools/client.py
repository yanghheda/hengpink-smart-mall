import json
from hashlib import sha256
from typing import Any, ClassVar, Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from pydantic import ValidationError

from app.tools.models import ToolCallTrace, ToolRequestEnvelope, ToolResponseEnvelope


class CommerceToolError(RuntimeError):
    """Tool 调用无法安全使用时的显式错误。"""

    def __init__(self, code: str, message: str) -> None:
        super().__init__(message)
        self.code = code


class ToolTransport(Protocol):
    """HTTP 传输边界，测试可替换而不启动真实网络。"""

    def post(self, path: str, payload: dict[str, Any], timeout_seconds: float) -> dict[str, Any]: ...


class UrlLibToolTransport:
    """只向配置的 Commerce API 基址发送 JSON 请求。"""

    def __init__(self, base_url: str, service_token: str | None = None) -> None:
        self._base_url = base_url.rstrip("/")
        self._service_token = service_token

    def post(self, path: str, payload: dict[str, Any], timeout_seconds: float) -> dict[str, Any]:
        body = json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if self._service_token:
            headers["Authorization"] = f"Bearer {self._service_token}"
        request = Request(f"{self._base_url}{path}", data=body, headers=headers, method="POST")
        try:
            with urlopen(request, timeout=timeout_seconds) as response:
                return json.loads(response.read().decode("utf-8"))
        except TimeoutError as error:
            raise CommerceToolError("TOOL_TIMEOUT", "Commerce Tool 调用超时") from error
        except HTTPError as error:
            raise CommerceToolError("TOOL_HTTP_ERROR", f"Commerce Tool 返回 HTTP {error.code}") from error
        except (URLError, OSError) as error:
            raise CommerceToolError("TOOL_UNAVAILABLE", "Commerce Tool 当前不可用") from error
        except (UnicodeDecodeError, json.JSONDecodeError) as error:
            raise CommerceToolError("TOOL_RESPONSE_INVALID", "Commerce Tool 响应不是合法 JSON") from error


class CommerceToolClient:
    """固定 Registry、严格信封与本地幂等保护组成的 Tool Client。"""

    PATHS: ClassVar[dict[str, str]] = {
        "search_products": "/internal/v1/tools/search-products",
        "get_product_specs": "/internal/v1/tools/get-product-specs",
        "get_price_offers": "/internal/v1/tools/get-price-offers",
        "calculate_final_price": "/internal/v1/tools/calculate-final-price",
        "score_candidates": "/internal/v1/tools/score-candidates",
    }

    def __init__(self, transport: ToolTransport, timeout_ms: int = 1000) -> None:
        if timeout_ms < 100 or timeout_ms > 5000:
            raise ValueError("Tool 超时必须在 100 到 5000 毫秒之间")
        self._transport = transport
        self._timeout_ms = timeout_ms
        self._request_hashes: dict[str, str] = {}
        self._responses: dict[str, ToolResponseEnvelope] = {}
        self.traces: list[ToolCallTrace] = []

    def call(
        self,
        tool_name: str,
        *,
        run_id: str,
        run_version: int,
        tool_call_id: str,
        dataset_version: str,
        input_data: dict[str, Any],
    ) -> dict[str, Any]:
        path = self.PATHS.get(tool_name)
        if path is None:
            raise CommerceToolError("TOOL_NOT_REGISTERED", "Tool 不在固定 Registry 中")
        envelope = ToolRequestEnvelope(
            runId=run_id,
            runVersion=run_version,
            toolCallId=tool_call_id,
            datasetVersion=dataset_version,
            timeoutMs=self._timeout_ms,
            input=input_data,
        )
        request_hash = sha256(envelope.model_dump_json().encode("utf-8")).hexdigest()
        previous_hash = self._request_hashes.get(tool_call_id)
        if previous_hash is not None and previous_hash != request_hash:
            raise CommerceToolError("TOOL_CALL_ID_CONFLICT", "同一 toolCallId 的请求内容不一致")
        if tool_call_id in self._responses:
            return self._responses[tool_call_id].data
        self._request_hashes[tool_call_id] = request_hash
        try:
            raw = self._transport.post(path, envelope.model_dump(), self._timeout_ms / 1000)
            response = ToolResponseEnvelope.model_validate(raw)
        except ValidationError as error:
            self._trace(tool_call_id, tool_name, "FAILED", None, "TOOL_RESPONSE_INVALID")
            raise CommerceToolError("TOOL_RESPONSE_INVALID", "Commerce Tool 响应不符合信封 Schema") from error
        except CommerceToolError as error:
            self._trace(tool_call_id, tool_name, "FAILED", None, error.code)
            raise
        if response.sourceVersion != dataset_version:
            self._trace(tool_call_id, tool_name, "FAILED", response.sourceVersion, "TOOL_VERSION_MISMATCH")
            raise CommerceToolError("TOOL_VERSION_MISMATCH", "Tool 数据版本与当前 Run 不一致")
        if response.status != "SUCCESS":
            code = response.errorCode or "TOOL_FAILED"
            self._trace(tool_call_id, tool_name, "FAILED", response.sourceVersion, code)
            raise CommerceToolError(code, "Commerce Tool 明确返回失败")
        self._responses[tool_call_id] = response
        self._trace(tool_call_id, tool_name, "SUCCESS", response.sourceVersion, None)
        return response.data

    def _trace(
        self,
        tool_call_id: str,
        tool_name: str,
        status: str,
        source_version: str | None,
        error_code: str | None,
    ) -> None:
        self.traces.append(
            ToolCallTrace(
                tool_call_id=tool_call_id,
                tool_name=tool_name,
                status=status,
                source_version=source_version,
                error_code=error_code,
            )
        )
