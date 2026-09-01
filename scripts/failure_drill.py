#!/usr/bin/env python3
"""执行单个依赖故障演练并校验显式降级证据。"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class DrillError(RuntimeError):
    """故障演练配置或验收失败。"""


def select_scenario(config: dict[str, Any], name: str) -> dict[str, Any]:
    for scenario in config.get("scenarios", []):
        if scenario.get("name") == name:
            return scenario
    raise DrillError(f"未知故障场景: {name}")


def evaluate_probe(scenario: dict[str, Any], status: int, body: str) -> dict[str, Any]:
    """状态码和降级标记必须同时满足，避免把静默降级当成功。"""
    expected_statuses = scenario.get("expectedStatuses", [200])
    missing_markers = [
        marker for marker in scenario.get("requiredMarkers", []) if marker not in body
    ]
    return {
        "status": status,
        "expectedStatuses": expected_statuses,
        "requiredMarkers": scenario.get("requiredMarkers", []),
        "missingMarkers": missing_markers,
        "passed": status in expected_statuses and not missing_markers,
    }


def _run_command(command: list[str], label: str) -> None:
    if not command or not all(isinstance(part, str) and part for part in command):
        raise DrillError(f"{label} 必须是非空参数数组")
    completed = subprocess.run(command, check=False)
    if completed.returncode != 0:
        raise DrillError(f"{label} 返回非零退出码: {completed.returncode}")


def _probe(scenario: dict[str, Any]) -> tuple[int, str]:
    probe = scenario["probe"]
    payload = json.dumps(probe["body"]).encode("utf-8") if "body" in probe else None
    headers = {str(key): str(value) for key, value in probe.get("headers", {}).items()}
    if payload is not None:
        headers.setdefault("Content-Type", "application/json")
    request = Request(
        probe["url"],
        data=payload,
        headers=headers,
        method=probe.get("method", "GET"),
    )
    try:
        with urlopen(
            request, timeout=float(probe.get("timeoutSeconds", 15))
        ) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")
    except URLError as error:
        raise DrillError(f"探针不可达: {probe['url']}: {error.reason}") from error


def run_drill(config: dict[str, Any], name: str, execute: bool) -> dict[str, Any]:
    scenario = select_scenario(config, name)
    if not execute:
        raise DrillError("真实故障演练必须显式传入 --execute")
    started_at = datetime.now(UTC).isoformat()
    stopped = False
    recovery: dict[str, Any] = {"attempted": False, "passed": False}
    evaluation: dict[str, Any] | None = None
    try:
        _run_command(scenario["stopCommand"], f"{name} 停止命令")
        stopped = True
        status, body = _probe(scenario)
        evaluation = evaluate_probe(scenario, status, body)
    finally:
        if stopped:
            recovery["attempted"] = True
            _run_command(scenario["startCommand"], f"{name} 恢复命令")
            recovery_status, recovery_body = _probe(scenario["recoveryScenario"])
            recovery_eval = evaluate_probe(
                scenario["recoveryScenario"], recovery_status, recovery_body
            )
            recovery.update(recovery_eval)
    if evaluation is None:
        raise DrillError("故障探针未产生结果")
    return {
        "schemaVersion": "failure-drill-report-v1",
        "scenario": name,
        "startedAt": started_at,
        "finishedAt": datetime.now(UTC).isoformat(),
        "metadata": config.get("metadata", {}),
        "degradation": evaluation,
        "recovery": recovery,
        "passed": evaluation["passed"] and recovery["passed"],
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument(
        "--scenario", required=True, choices=("model", "qdrant", "redis", "python")
    )
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    try:
        config = json.loads(args.config.read_text(encoding="utf-8"))
        report = run_drill(config, args.scenario, args.execute)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"故障演练报告已生成: {args.output}")
        return 0 if report["passed"] else 1
    except (DrillError, OSError, ValueError) as error:
        print(f"故障演练失败: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
