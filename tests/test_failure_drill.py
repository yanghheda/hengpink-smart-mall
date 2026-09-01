import unittest

from scripts.failure_drill import DrillError, evaluate_probe, select_scenario


class FailureDrillTest(unittest.TestCase):
    def test_unknown_scenario_is_rejected(self) -> None:
        with self.assertRaisesRegex(DrillError, "未知故障场景"):
            select_scenario({"scenarios": []}, "redis")

    def test_missing_degradation_marker_fails_drill(self) -> None:
        scenario = {
            "name": "qdrant",
            "expectedStatuses": [200],
            "requiredMarkers": ["QDRANT_UNAVAILABLE"],
        }
        result = evaluate_probe(scenario, 200, '{"degradationCodes": []}')
        self.assertFalse(result["passed"])
        self.assertEqual(result["missingMarkers"], ["QDRANT_UNAVAILABLE"])

    def test_visible_degradation_marker_passes_drill(self) -> None:
        scenario = {
            "name": "model",
            "expectedStatuses": [200, 206],
            "requiredMarkers": ["MODEL_UNAVAILABLE"],
        }
        result = evaluate_probe(
            scenario,
            206,
            '{"generationType":"TEMPLATE_FALLBACK","degradationReasons":["MODEL_UNAVAILABLE"]}',
        )
        self.assertTrue(result["passed"])


if __name__ == "__main__":
    unittest.main()
