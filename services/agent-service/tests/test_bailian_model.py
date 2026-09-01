import json
from io import BytesIO

from app.model.bailian import BailianModel, bailian_compatible_schema


class FakeResponse(BytesIO):
    def __enter__(self):
        return self

    def __exit__(self, *_args):
        self.close()


class CapturingOpener:
    def __init__(self, content: dict[str, object]) -> None:
        self.content = content
        self.request = None

    def open(self, request, timeout):
        self.request = request
        response = {"choices": [{"message": {"content": json.dumps(self.content)}}]}
        return FakeResponse(json.dumps(response).encode())


def test_bailian_request_uses_low_reasoning_and_json_schema() -> None:
    model = BailianModel(
        api_key="secret",
        base_url="https://example.test/compatible-mode/v1",
        model_name="qwen3.8-flash",
        reasoning_effort="low",
    )
    opener = CapturingOpener({"category": "PHONE"})
    model._opener = opener

    result = model._structured_completion(
        messages=[{"role": "user", "content": "推荐手机"}],
        schema_name="intent",
        schema={"type": "object"},
    )

    body = json.loads(opener.request.data)
    assert result == {"category": "PHONE"}
    assert body["model"] == "qwen3.8-flash"
    assert body["reasoning_effort"] == "low"
    assert body["response_format"]["type"] == "json_schema"


def test_bailian_schema_replaces_unsupported_lookahead_without_mutating_source() -> None:
    source = {"type": "string", "pattern": r"^(?!bad).+$"}

    compatible = bailian_compatible_schema(source)

    assert source["pattern"] == r"^(?!bad).+$"
    assert compatible["pattern"] == r"^-?[0-9]+([.][0-9]+)?$"
