"""Offline evaluation harness (FF-FEA-002).

Runs the deterministic pipeline over a generated fixture dataset and reports
metrics with versions, config and failure cases. No real-world accuracy claim is
ever made; this is local feasibility evidence only.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

from .pipeline import run_pipeline
from .quality import analyze_error


def evaluate_dataset(dataset_dir: Path, profile: dict, brief: list[dict] | None = None,
                     near_duplicate_threshold: float = 0.75) -> dict:
    manifest_path = dataset_dir / "dataset-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    cases = manifest["cases"]
    results = []
    for case in cases:
        path = dataset_dir / case["file"]
        if not path.exists():
            results.append({"id": case["id"], "error": "missing file"})
            continue
        status = None
        decision = None
        try:
            rr = run_pipeline(path, profile, brief_assertions=brief)
            status = rr.status
            decision = rr.decision
            findings = rr.findings
            verdicts = [f["verdict"] for f in findings]
        except Exception as exc:
            results.append({"id": case["id"], "error": str(exc)})
            continue
        expected = case.get("expected", [])
        results.append({
            "id": case["id"],
            "file": case["file"],
            "expected": expected,
            "status": status,
            "decision": decision.value if decision else None,
            "automaticReject": decision.automatic if decision else False,
            "violatedRules": [f["ruleId"] for f in findings if f["verdict"] == "VIOLATED"],
            "unknownRules": [f["ruleId"] for f in findings if f["verdict"] == "UNKNOWN"],
        })
    # Deterministic expected-result mapping for the fixture set (authoritative).
    expectation_map = {
        "normal_vertical": "ELIGIBLE",
        "wrong_aspect": "REJECT",
        "silent_vertical": "REJECT",
        "black_video": "REJECT",
        "freeze_video": "REJECT",
        "too_short": "REJECT",
        "duplicate_source": "ELIGIBLE",
        "duplicate_copy": "ELIGIBLE",
        "near_duplicate": "ELIGIBLE",
        "low_resolution": "REJECT",
    }
    rows = {r["id"]: r for r in results}
    matched = 0
    tested = 0
    failures = []
    for cid, expected_decision in expectation_map.items():
        row = rows.get(cid)
        if not row or row.get("error"):
            failures.append({"id": cid, "problem": "no result"})
            continue
        tested += 1
        if row.get("decision") == expected_decision:
            matched += 1
        else:
            failures.append({"id": cid, "expected": expected_decision, "actual": row.get("decision"),
                             "violated": row.get("violatedRules")})
    report = {
        "schemaVersion": "1.0.0",
        "kind": "LOCAL_FEASIBILITY_EVALUATION",
        "evaluatedAtUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "dataset": str(dataset_dir),
        "casesTotal": len(cases),
        "tested": tested,
        "matchedExpected": matched,
        "failures": failures,
        "unexpectedAnalysisErrors": [r for r in results if r.get("status") == "FAILED"],
        "notes": [
            "SYNTHETIC_AND_LOCAL_ONLY: not a claim of real-world accuracy",
            "deterministic_qa_only; semantic provider is the Fake provider",
        ],
    }
    return report


def main_eval(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="Run offline evaluation on a fixture dataset.")
    ap.add_argument("--dataset", default="experiments/fixtures/generated")
    ap.add_argument("--profile", required=True, help="quality profile JSON")
    ap.add_argument("--sv", action="store_true", help="validate schemas before run")
    args = ap.parse_args(argv)
    profile = json.loads(Path(args.profile).read_text(encoding="utf-8"))
    report = evaluate_dataset(Path(args.dataset), profile)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main_eval(sys.argv[1:]))
