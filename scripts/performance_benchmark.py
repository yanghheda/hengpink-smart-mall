#!/usr/bin/env python3
"""运行首屏、搜索和重算 HTTP 基准，并生成可追溯报告。"""

from __future__ import annotations

import argparse
import json
import math
import platform
import subprocess
import sys
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


class BenchmarkError(RuntimeError):
    """基准配置或执行结果不满足验收条件。"""


def percentile(samples: list[float], value: int) -> float:
    """使用 nearest-rank 计算百分位，避免小样本插值掩盖慢请求。"""
    if not samples:
        raise BenchmarkError("没有成功样本，无法计算百分位")
    ordered = sorted(samples)
    rank = max(1, math.ceil(value / 100 * len(ordered)))
    return ordered[rank - 1]


def _machine_metadata() -> dict[str, str]:
    return {
        "hostname": platform.node() or "unknown",
        "platform": platform.platform(),
        "pythonVersion": platform.python_version(),
        "processor": platform.processor() or "unknown",
    }


def build_report(
    metadata: dict[str, str],
    samples_by_name: dict[str, list[float]],
    attempts_by_name: dict[str, int],
) -> dict[str, Any]:
    """把原始耗时转换为带环境版本和分母的结构化报告。"""
    for field in ("gitSha", "datasetVersion", "scoringVersion"):
        if not str(metadata.get(field, "")).strip():
            raise BenchmarkError(f"报告缺少必要版本字段: {field}")
    benchmarks: dict[str, Any] = {}
    for name, samples in samples_by_name.items():
        if not samples:
            raise BenchmarkError(f"{name} 没有成功样本")
        attempts = attempts_by_name.get(name, 0)
        if attempts < len(samples) or attempts <= 0:
            raise BenchmarkError(f"{name} 的请求分母非法")
        if len(samples) != attempts:
            raise BenchmarkError(f"{name} 存在非成功响应: {len(samples)}/{attempts}")
        benchmarks[name] = {
            "attempts": attempts,
            "successes": len(samples),
            "successRate": round(len(samples) / attempts, 4),
            "minMs": round(min(samples), 3),
            "p50Ms": round(percentile(samples, 50), 3),
            "p75Ms": round(percentile(samples, 75), 3),
            "p95Ms": round(percentile(samples, 95), 3),
            "maxMs": round(max(samples), 3),
        }
    return {
        "schemaVersion": "performance-report-v1",
        "generatedAt": datetime.now(UTC).isoformat(),
        "metadata": metadata,
        "machine": _machine_metadata(),
        "benchmarks": benchmarks,
    }


def _request(scenario: dict[str, Any], timeout_seconds: float) -> tuple[int, float]:
    body = scenario.get("body")
    payload = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {
        str(key): str(value) for key, value in scenario.get("headers", {}).items()
    }
    if payload is not None:
        headers.setdefault("Content-Type", "application/json")
    request = Request(
        scenario["url"],
        data=payload,
        headers=headers,
        method=scenario.get("method", "GET"),
    )
    started = time.perf_counter_ns()
    try:
        with urlopen(request, timeout=timeout_seconds) as response:
            response.read()
            status = response.status
    except HTTPError as error:
        error.read()
        status = error.code
    except URLError as error:
        raise BenchmarkError(
            f"请求不可达: {scenario['url']}: {error.reason}"
        ) from error
    elapsed_ms = (time.perf_counter_ns() - started) / 1_000_000
    return status, elapsed_ms


def run_benchmarks(config: dict[str, Any]) -> dict[str, Any]:
    """执行预热与正式样本，任一非预期状态都会保留为失败分母。"""
    samples_by_name: dict[str, list[float]] = {}
    attempts_by_name: dict[str, int] = {}
    for scenario in config.get("scenarios", []):
        name = str(scenario["name"])
        iterations = int(scenario.get("iterations", 20))
        warmups = int(scenario.get("warmups", 2))
        expected = set(scenario.get("expectedStatuses", [200]))
        timeout_seconds = float(scenario.get("timeoutSeconds", 10))
        if iterations < 1:
            raise BenchmarkError(f"{name} 的 iterations 必须大于零")
        for _ in range(warmups):
            _request(scenario, timeout_seconds)
        samples: list[float] = []
        for _ in range(iterations):
            status, elapsed_ms = _request(scenario, timeout_seconds)
            if status in expected:
                samples.append(elapsed_ms)
        samples_by_name[name] = samples
        attempts_by_name[name] = iterations
    if not samples_by_name:
        raise BenchmarkError("配置中没有基准场景")
    return build_report(config.get("metadata", {}), samples_by_name, attempts_by_name)


def _markdown(report: dict[str, Any]) -> str:
    lines = [
        "# P14-S04 性能报告",
        "",
        f"- 生成时间：{report['generatedAt']}",
        f"- Git：{report['metadata']['gitSha']}",
        f"- Dataset：{report['metadata']['datasetVersion']}",
        f"- Scoring：{report['metadata']['scoringVersion']}",
        f"- 机器：{report['machine']['hostname']} / {report['machine']['platform']}",
        "",
        "| 场景 | 成功/总数 | 成功率 | P50(ms) | P75(ms) | P95(ms) | 最大(ms) |",
        "|---|---:|---:|---:|---:|---:|---:|",
    ]
    for name, item in report["benchmarks"].items():
        lines.append(
            f"| {name} | {item['successes']}/{item['attempts']} | "
            f"{item['successRate']:.2%} | {item['p50Ms']} | {item['p75Ms']} | "
            f"{item['p95Ms']} | {item['maxMs']} |"
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    try:
        config = json.loads(args.config.read_text(encoding="utf-8"))
        config.setdefault("metadata", {}).setdefault(
            "gitSha",
            subprocess.run(
                ["git", "rev-parse", "--short", "HEAD"],
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip(),
        )
        report = run_benchmarks(config)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(
            json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        args.output.with_suffix(".md").write_text(_markdown(report), encoding="utf-8")
        print(f"性能报告已生成: {args.output}")
        return 0
    except (BenchmarkError, OSError, ValueError, subprocess.SubprocessError) as error:
        print(f"性能基准失败: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
