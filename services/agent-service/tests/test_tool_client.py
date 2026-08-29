import pytest
from tool_fakes import DATASET_VERSION, DeterministicToolTransport

from app.tools.client import CommerceToolClient, CommerceToolError


def call(client: CommerceToolClient, tool_call_id: str = "TC-1", input_data: dict | None = None):
    return client.call(
        "search_products",
        run_id="RUN-1",
        run_version=1,
        tool_call_id=tool_call_id,
        dataset_version=DATASET_VERSION,
        input_data=input_data or {"categoryId": "PHONE"},
    )


def test_same_tool_call_id_reuses_response_but_rejects_changed_request() -> None:
    transport = DeterministicToolTransport()
    client = CommerceToolClient(transport)

    assert call(client) == call(client)
    assert len(transport.calls) == 1

    with pytest.raises(CommerceToolError, match="请求内容不一致") as captured:
        call(client, input_data={"categoryId": "PHONE", "limit": 1})
    assert captured.value.code == "TOOL_CALL_ID_CONFLICT"


def test_dataset_version_mismatch_is_visible_and_never_used() -> None:
    class WrongVersionTransport(DeterministicToolTransport):
        def post(self, path, payload, timeout_seconds):
            response = super().post(path, payload, timeout_seconds)
            response["sourceVersion"] = "commerce-demo-other"
            return response

    client = CommerceToolClient(WrongVersionTransport())

    with pytest.raises(CommerceToolError) as captured:
        call(client)

    assert captured.value.code == "TOOL_VERSION_MISMATCH"
    assert client.traces[-1].status == "FAILED"


def test_fixed_phone_flow_uses_five_traced_tools_and_java_scores() -> None:
    from test_graph import initial_state

    from app.graph.workflow import StubGraphModel, build_shopping_decision_graph

    client = CommerceToolClient(DeterministicToolTransport())
    result = build_shopping_decision_graph(StubGraphModel(), client).invoke(initial_state())

    assert [card["skuId"] for card in result["score_cards"]] == [
        "SKU-PHONE-1",
        "SKU-PHONE-2",
        "SKU-PHONE-3",
    ]
    assert [card["finalScore"] for card in result["score_cards"]] == ["91", "90", "89"]
    assert [trace["tool_name"] for trace in result["tool_calls"]] == [
        "search_products",
        "get_product_specs",
        "get_price_offers",
        "calculate_final_price",
        "score_candidates",
    ]
