import unittest

from scripts.performance_benchmark import BenchmarkError, build_report, percentile


class PerformanceBenchmarkTest(unittest.TestCase):
    def test_percentile_uses_nearest_rank(self) -> None:
        self.assertEqual(percentile([1, 2, 3, 4, 100], 95), 100)

    def test_report_rejects_missing_version_metadata(self) -> None:
        with self.assertRaisesRegex(BenchmarkError, "datasetVersion"):
            build_report(
                {"gitSha": "abc", "scoringVersion": "scoring-v1"},
                {"search": [10.0]},
                {"search": 1},
            )

    def test_report_rejects_empty_samples(self) -> None:
        metadata = {
            "gitSha": "abc",
            "datasetVersion": "commerce-demo-2026.08.1",
            "scoringVersion": "scoring-v1",
        }
        with self.assertRaisesRegex(BenchmarkError, "没有成功样本"):
            build_report(metadata, {"search": []}, {"search": 1})

    def test_report_contains_machine_versions_and_percentiles(self) -> None:
        metadata = {
            "gitSha": "abc",
            "datasetVersion": "commerce-demo-2026.08.1",
            "scoringVersion": "scoring-v1",
        }
        report = build_report(metadata, {"search": [10.0, 20.0]}, {"search": 2})
        self.assertEqual(
            report["metadata"]["datasetVersion"], "commerce-demo-2026.08.1"
        )
        self.assertTrue(report["machine"]["platform"])
        self.assertEqual(report["benchmarks"]["search"]["p95Ms"], 20.0)
        self.assertEqual(report["benchmarks"]["search"]["successRate"], 1.0)

    def test_report_rejects_partial_http_success(self) -> None:
        metadata = {
            "gitSha": "abc",
            "datasetVersion": "commerce-demo-2026.08.1",
            "scoringVersion": "scoring-v1",
        }
        with self.assertRaisesRegex(BenchmarkError, "非成功响应"):
            build_report(metadata, {"search": [10.0]}, {"search": 2})


if __name__ == "__main__":
    unittest.main()
