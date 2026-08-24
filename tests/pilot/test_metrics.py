from __future__ import annotations

import math
import unittest

from scripts.pilot.calculate_metrics import (
    calculate_metrics,
    cohen_kappa,
    proportion_metric,
    time_savings_metric,
    wilson_interval,
)
from scripts.pilot.pilotlib import PilotError


def annotation(**overrides: str) -> dict[str, str]:
    row = {
        "candidate_id": "C-1",
        "batch_id": "B-1",
        "blind_review_id": "BR-1",
        "machine_disposition": "ANALYZED",
        "operational_full_watch": "true",
        "baseline_full_watch_seconds": "10",
        "assisted_review_seconds": "5",
        "selected_for_delivery": "false",
        "audit_included": "true",
        "adjudicated_quality": "ACCEPTABLE",
        "delivery_obvious_defect": "",
    }
    row.update(overrides)
    return row


class WilsonIntervalTest(unittest.TestCase):
    def test_zero_denominator_is_none(self) -> None:
        self.assertIsNone(wilson_interval(0, 0))

    def test_interval_is_bounded_and_contains_observation(self) -> None:
        interval = wilson_interval(5, 10)
        assert interval is not None
        self.assertLessEqual(0, interval["low"])
        self.assertLess(interval["low"], 0.5)
        self.assertGreater(interval["high"], 0.5)
        self.assertLessEqual(interval["high"], 1)

    def test_invalid_numerator_fails(self) -> None:
        with self.assertRaises(PilotError):
            wilson_interval(2, 1)


class ProportionMetricTest(unittest.TestCase):
    def test_zero_denominator_emits_json_safe_null(self) -> None:
        metric = proportion_metric(
            numerator=0,
            denominator=0,
            missing_count=0,
            definition="empty",
            confidence=0.95,
            limitations=[],
        )
        self.assertIsNone(metric["value"])
        self.assertIsNone(metric["confidenceInterval"])
        self.assertEqual("ZERO_DENOMINATOR", metric["reason"])

    def test_missing_values_remain_visible(self) -> None:
        metric = proportion_metric(
            numerator=1,
            denominator=2,
            missing_count=3,
            definition="observed",
            confidence=0.95,
            limitations=[],
        )
        self.assertEqual(0.5, metric["value"])
        self.assertFalse(metric["complete"])
        self.assertEqual(3, metric["missingCount"])


class TimeSavingsMetricTest(unittest.TestCase):
    def test_time_weighted_rate_uses_seconds_not_candidate_count(self) -> None:
        metric = time_savings_metric(
            [(100.0, 50.0), (10.0, 10.0)], missing_count=0, confidence=0.95
        )
        self.assertAlmostEqual(50 / 110, metric["value"])
        self.assertEqual(50.0, metric["savedSeconds"])
        self.assertEqual("PAIRED_CANDIDATE_BOOTSTRAP_PERCENTILE", metric["confidenceInterval"]["method"])

    def test_zero_baseline_is_json_safe(self) -> None:
        metric = time_savings_metric([(0.0, 0.0)], missing_count=0, confidence=0.95)
        self.assertIsNone(metric["value"])
        self.assertEqual("ZERO_BASELINE_SECONDS", metric["reason"])


class AgreementTest(unittest.TestCase):
    def test_no_label_variance_does_not_divide_by_zero(self) -> None:
        result = cohen_kappa({"A": "EXCELLENT"}, {"A": "EXCELLENT"})
        self.assertIsNone(result["cohenKappa"])
        self.assertEqual("NO_LABEL_VARIANCE", result["reason"])

    def test_disagreement_is_finite(self) -> None:
        result = cohen_kappa(
            {"A": "EXCELLENT", "B": "DEFECTIVE", "C": "DEFECTIVE"},
            {"A": "EXCELLENT", "B": "ACCEPTABLE", "C": "DEFECTIVE"},
        )
        self.assertTrue(math.isfinite(result["cohenKappa"]))
        self.assertEqual(3, result["overlapCount"])


class MetricPipelineTest(unittest.TestCase):
    def test_empty_false_kill_and_delivery_denominators_are_explicit(self) -> None:
        rows = [annotation()]
        reviews = [{"blind_review_id": "BR-1", "quality_label": "ACCEPTABLE"}]
        result = calculate_metrics(rows, reviews, reviews, {"batches": []})
        self.assertIsNone(result["metrics"]["falseKillRate"]["value"])
        self.assertIsNone(result["metrics"]["missRate"]["value"])
        self.assertFalse(result["valueConclusionAuthorized"])

    def test_analysis_error_is_not_counted_as_false_kill(self) -> None:
        rows = [
            annotation(
                machine_disposition="ANALYSIS_ERROR",
                adjudicated_quality="EXCELLENT",
            )
        ]
        reviews = [{"blind_review_id": "BR-1", "quality_label": "EXCELLENT"}]
        result = calculate_metrics(rows, reviews, reviews, {"batches": []})
        self.assertEqual(0, result["metrics"]["falseKillRate"]["denominator"])

    def test_acceptable_auto_reject_is_a_false_kill(self) -> None:
        rows = [
            annotation(
                machine_disposition="AUTO_REJECT",
                adjudicated_quality="ACCEPTABLE",
            )
        ]
        reviews = [{"blind_review_id": "BR-1", "quality_label": "ACCEPTABLE"}]
        result = calculate_metrics(rows, reviews, reviews, {"batches": []})
        false_kill = result["metrics"]["falseKillRate"]
        self.assertEqual(1, false_kill["numerator"])
        self.assertEqual(1.0, false_kill["value"])

    def test_duplicate_candidate_id_fails(self) -> None:
        rows = [annotation(), annotation(blind_review_id="BR-2")]
        reviews = [
            {"blind_review_id": "BR-1", "quality_label": "ACCEPTABLE"},
            {"blind_review_id": "BR-2", "quality_label": "ACCEPTABLE"},
        ]
        with self.assertRaises(PilotError):
            calculate_metrics(rows, reviews, reviews, {"batches": []})


if __name__ == "__main__":
    unittest.main()
