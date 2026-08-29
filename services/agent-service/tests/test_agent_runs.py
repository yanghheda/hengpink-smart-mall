import asyncio

from httpx import ASGITransport, AsyncClient

from app.main import AgentRunRequest, AgentSettings, StubRunExecutor, create_app


def settings() -> AgentSettings:
    return AgentSettings(
        environment="test",
        tool_api_base_url="http://commerce.test",
        qdrant_url="http://qdrant.test",
        model_provider="stub",
        model_name="stub-v1",
        model_api_key=None,
    )


def request_body() -> dict[str, object]:
    return {
        "runId": "01J5D0M8RZ0000000000000021",
        "sessionId": "01J5D0M8RZ0000000000000020",
        "runVersion": 1,
        "versions": {"dataset": "commerce-demo-2026.08.0"},
        "input": {"messages": [], "memorySnapshot": [], "previousIntent": None},
        "callback": {"baseUrlId": "commerce-api-internal", "callbackToken": "signed-token"},
        "budget": {"softTimeoutMs": 12000, "hardTimeoutMs": 20000, "maxModelCalls": 5},
    }


def test_agent_run_returns_202_and_duplicate_run_is_idempotent() -> None:
    async def scenario() -> None:
        executor = StubRunExecutor()
        app = create_app(settings=settings(), run_executor=executor)
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://agent.test"
        ) as client:
            first = await client.post("/internal/v1/agent-runs", json=request_body())
            second = await client.post("/internal/v1/agent-runs", json=request_body())
            await asyncio.sleep(0)

        assert first.status_code == 202
        assert first.json() == {"runId": request_body()["runId"], "status": "ACCEPTED"}
        assert second.status_code == 202
        assert second.json()["status"] in {"ACCEPTED", "RUNNING", "COMPLETED"}
        assert executor.started_run_ids == [request_body()["runId"]]

    asyncio.run(scenario())


def test_same_run_id_with_changed_payload_returns_conflict() -> None:
    async def scenario() -> None:
        app = create_app(settings=settings(), run_executor=StubRunExecutor())
        changed = request_body()
        changed["runVersion"] = 2
        async with AsyncClient(
            transport=ASGITransport(app=app), base_url="http://agent.test"
        ) as client:
            first = await client.post("/internal/v1/agent-runs", json=request_body())
            conflict = await client.post("/internal/v1/agent-runs", json=changed)

        assert first.status_code == 202
        assert conflict.status_code == 409
        assert conflict.json()["error"]["code"] == "AGENT_RUN_PAYLOAD_CONFLICT"

    asyncio.run(scenario())


def test_stub_calls_step_before_complete_with_same_run_token() -> None:
    class RecordingSender:
        def __init__(self) -> None:
            self.calls: list[tuple[str, str, dict[str, object]]] = []

        async def post(self, path: str, token: str, body: dict[str, object]) -> None:
            self.calls.append((path, token, body))

    async def scenario() -> None:
        sender = RecordingSender()
        executor = StubRunExecutor(sender)
        await executor.execute(AgentRunRequest.model_validate(request_body()))

        assert [call[0].rsplit("/", 1)[-1] for call in sender.calls] == ["steps", "complete"]
        assert [call[1] for call in sender.calls] == ["signed-token", "signed-token"]
        assert all(len(str(call[2]["contentHash"])) == 64 for call in sender.calls)

    asyncio.run(scenario())
