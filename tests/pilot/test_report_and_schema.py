from __future__ import annotations

import json
from pathlib import Path
import unittest

from scripts.pilot.generate_report import build_report, render_markdown
from scripts.pilot.pilotlib import CLASSIFICATION, OWNER_ONLY_STATE_NAMES, REPO_ROOT


def rate(value: float) -> dict[str, object]:
    return {
        "numerator": 1,
        "denominator": 2,
        "value": value,
        "confidenceInterval": {"low": 0.1, "high": 0.9},
        "missingCount": 0,
        "sampleSizeCaution": True,
    }


class ReportBoundaryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.metrics = {
            "classification": CLASSIFICATION,
            "metrics": {
                "manualViewingTimeSavingsRate": {
                    **rate(0.5),
                    "baselineSeconds": 10,
                    "assistedSeconds": 5,
                    "savedSeconds": 5,
                },
                "fullWatchAvoidanceRate": rate(0.5),
                "falseKillRate": rate(0.5),
                "missRate": rate(0.5),
                "batchProcessingTime": {"medianSeconds": 42},
                "blindReviewAgreement": {
                    "cohenKappa": 0.5,
                    "rawAgreement": 0.75,
                    "overlapCount": 4,
                },
            },
            "sampleLimitations": ["small"],
        }
        self.validation = {
            "classification": CLASSIFICATION,
            "status": "PASS",
            "counts": {"candidateRecordCount": 310},
        }
        self.provenance = {
            "classification": CLASSIFICATION,
            "rehearsalId": "syn-test",
            "inputSetSha256": "a" * 64,
            "source": {"gitCommit": "b" * 40},
        }
        self.summary = {
            "classification": CLASSIFICATION,
            "datasetId": "syn-dataset",
            "seed": 1,
        }

    def test_report_is_never_decision_eligible(self) -> None:
        report = build_report(self.metrics, self.validation, self.provenance, self.summary)
        self.assertFalse(report["decision"]["realPilotValueDecisionEligible"])

    def test_markdown_has_fact_inference_unknown_boundaries(self) -> None:
        report = build_report(self.metrics, self.validation, self.provenance, self.summary)
        markdown = render_markdown(report)
        self.assertIn("FACT", markdown)
        self.assertIn("INFERENCE", markdown)
        self.assertIn("UNKNOWN", markdown)
        for state in OWNER_ONLY_STATE_NAMES:
            self.assertNotIn(state, markdown)


class ManifestSchemaTest(unittest.TestCase):
    def test_schema_enforces_synthetic_provider_and_data_boundary(self) -> None:
        path = REPO_ROOT / "experiments" / "pilot" / "schemas" / "pilot-manifest.schema.json"
        schema = json.loads(path.read_text(encoding="utf-8"))
        serialized = json.dumps(schema, sort_keys=True)
        self.assertIn("SYNTHETIC_REHEARSAL", serialized)
        self.assertIn('"realCallsAllowed": {"const": false}', serialized)
        self.assertIn('"containsRealCustomerMedia": {"const": false}', serialized)


if __name__ == "__main__":
    unittest.main()
