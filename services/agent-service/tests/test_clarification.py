from copy import deepcopy

from app.clarification.service import ClarificationPlanner, merge_intents
from app.graph.state import InitialGraphState
from app.graph.workflow import StubGraphModel, build_shopping_decision_graph


def intent(**overrides: object) -> dict[str, object]:
    value: dict[str, object] = {
        "category": "PHONE",
        "recipient": None,
        "budget": None,
        "hard_constraints": [],
        "usage_scenarios": [],
        "preferences": [],
        "memberships": [],
        "inferences": [],
    }
    value.update(overrides)
    return value


def graph_state(
    *, previous_intent: dict[str, object] | None = None, clarification_round: int = 0
) -> dict[str, object]:
    return InitialGraphState(
        run_id="run-2",
        session_id="session-1",
        run_version=2,
        user_id_ref="user-ref-1",
        dataset_version="dataset-v1",
        prompt_version="intent-v1",
        scoring_version="score-v1",
        pricing_rule_version="price-v1",
        messages=[{"role": "user", "content": "预算三千以内"}],
        budget={"max_model_calls": 5},
        previous_intent=previous_intent,
        clarification_round=clarification_round,
    ).to_graph_state()


def test_high_value_questions_are_ordered_and_limited_to_two() -> None:
    plan = ClarificationPlanner().plan(
        intent(category=None), messages=[{"role": "user", "content": "帮我选一个"}], round_count=0
    )

    assert [question.field for question in plan.questions] == ["category", "budget"]
    assert all(question.question_value >= 0.35 for question in plan.questions)
    assert all(2 <= len(question.options) <= 4 for question in plan.questions)
    assert all("暂不确定" in question.options for question in plan.questions)


def test_conflicting_hard_constraint_is_asked_before_budget() -> None:
    plan = ClarificationPlanner().plan(
        intent(
            hard_constraints=[
                {"name": "screen", "operator": "EQ", "value": "CURVED"},
                {"name": "screen", "operator": "NE", "value": "CURVED"},
            ]
        ),
        messages=[{"role": "user", "content": "帮我选手机"}],
        round_count=0,
    )

    assert [question.field for question in plan.questions] == [
        "hard_constraint_conflict",
        "budget",
    ]


def test_direct_recommendation_skips_soft_question_and_reduces_confidence() -> None:
    plan = ClarificationPlanner().plan(
        intent(), messages=[{"role": "user", "content": "手机直接推荐，不用再问"}], round_count=0
    )

    assert plan.questions == []
    assert plan.confidence_penalty == "0.15"
    assert plan.reason == "USER_SKIPPED_SOFT_CLARIFICATION"


def test_direct_recommendation_cannot_skip_missing_category() -> None:
    plan = ClarificationPlanner().plan(
        intent(category=None),
        messages=[{"role": "user", "content": "直接推荐，不用再问"}],
        round_count=0,
    )

    assert plan.questions[0].field == "category"


def test_round_limit_continues_with_explicit_confidence_penalty() -> None:
    plan = ClarificationPlanner().plan(
        intent(), messages=[{"role": "user", "content": "手机"}], round_count=2
    )

    assert plan.questions == []
    assert plan.confidence_penalty == "0.15"
    assert plan.reason == "CLARIFICATION_ROUND_LIMIT_REACHED"


def test_previous_intent_is_merged_without_mutating_persisted_snapshot() -> None:
    previous = intent(
        category="PHONE",
        recipient="PARENTS",
        hard_constraints=[
            {"name": "storage_gb", "operator": "GTE", "value": "128", "source": "USER_EXPLICIT"}
        ],
    )
    snapshot = deepcopy(previous)
    current = intent(category=None, budget={"max": "3000.00", "currency": "CNY"})

    merged = merge_intents(previous, current)

    assert merged["category"] == "PHONE"
    assert merged["recipient"] == "PARENTS"
    assert merged["budget"] == {"max": "3000.00", "currency": "CNY"}
    assert merged["hard_constraints"] == previous["hard_constraints"]
    assert previous == snapshot


def test_graph_interrupts_for_questions_then_restores_old_intent_on_new_run() -> None:
    first_graph = build_shopping_decision_graph(StubGraphModel(intent_output=intent(category=None)))
    first = first_graph.invoke(graph_state())

    assert first["completed_nodes"] == ["load_context", "intent", "clarification"]
    assert len(first["clarification"]["questions"]) == 2
    assert first["candidates"] == []

    previous = intent(category="PHONE", recipient="PARENTS")
    resumed_graph = build_shopping_decision_graph(
        StubGraphModel(
            intent_output=intent(
                category=None,
                budget={"max": "3000.00", "currency": "CNY"},
                usage_scenarios=["DAILY_COMMUNICATION"],
            )
        )
    )
    resumed = resumed_graph.invoke(graph_state(previous_intent=previous, clarification_round=1))

    assert resumed["intent"]["category"] == "PHONE"
    assert resumed["intent"]["recipient"] == "PARENTS"
    assert resumed["intent"]["budget"]["max"] == "3000.00"
    assert "product" in resumed["completed_nodes"]
