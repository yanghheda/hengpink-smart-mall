from copy import deepcopy

import pytest
from pydantic import ValidationError

from app.graph.state import InitialGraphState
from app.graph.workflow import StubGraphModel, build_shopping_decision_graph


def initial_state() -> dict[str, object]:
    return InitialGraphState(
        run_id="01J5D0M8RZ0000000000000021",
        session_id="01J5D0M8RZ0000000000000020",
        run_version=1,
        user_id_ref="user-ref-1",
        dataset_version="commerce-demo-2026.08.1",
        prompt_version="graph-skeleton-v1",
        scoring_version="phone-score-v1",
        pricing_rule_version="promotion-v1",
        messages=[{"role": "user", "content": "推荐一台适合长辈的手机"}],
        budget={"max_model_calls": 5},
    ).to_graph_state()


def test_initial_state_rejects_invalid_identity_and_budget() -> None:
    with pytest.raises(ValidationError):
        InitialGraphState(
            run_id="",
            session_id="session-1",
            run_version=0,
            user_id_ref="user-ref-1",
            dataset_version="dataset-v1",
            prompt_version="prompt-v1",
            scoring_version="score-v1",
            pricing_rule_version="price-v1",
            messages=[],
            budget={"max_model_calls": -1},
        )


def test_success_route_is_deterministic_and_keeps_identity_unchanged() -> None:
    source = initial_state()
    source_snapshot = deepcopy(source)
    graph = build_shopping_decision_graph(StubGraphModel())

    result = graph.invoke(source)

    assert result["completed_nodes"] == [
        "load_context",
        "intent",
        "clarification",
        "product",
        "review_stub",
        "price",
        "score",
        "report_stub",
        "validate",
    ]
    for field in (
        "run_id",
        "session_id",
        "run_version",
        "user_id_ref",
        "dataset_version",
        "prompt_version",
        "scoring_version",
        "pricing_rule_version",
        "budget",
    ):
        assert result[field] == source_snapshot[field]
    assert source == source_snapshot
    assert result["report"]["generation_type"] == "STUB"
    assert result["validation"]["valid"] is True


def test_no_candidate_route_stops_before_analysis_and_never_fabricates_report() -> None:
    graph = build_shopping_decision_graph(StubGraphModel(candidate_ids=[]))

    result = graph.invoke(initial_state())

    assert result["completed_nodes"] == [
        "load_context",
        "intent",
        "clarification",
        "product",
        "no_result",
    ]
    assert result["candidates"] == []
    assert result["report"] is None
    assert result["warnings"] == ["NO_MATCHED_CANDIDATE"]


def test_model_output_cannot_supply_amount_or_final_score() -> None:
    graph = build_shopping_decision_graph(
        StubGraphModel(
            report_text="首选引用确定性结果", attempted_amount="0.01", attempted_score=100
        )
    )

    result = graph.invoke(initial_state())

    assert result["price_plans"]["sku-stub-1"][0]["amount"] == "3999.00"
    assert result["score_cards"][0]["final_score"] == 80
    assert "attempted_amount" not in result["report"]
    assert "attempted_score" not in result["report"]
