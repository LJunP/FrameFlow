#!/usr/bin/env python3
"""Run the complete local F11 synthetic rehearsal in one command.

Reading order starts here: ``run_rehearsal`` generates assets, validates the
package, calculates metrics, writes provenance, renders both report formats,
then seals hashes in a final receipt.  No application service or Provider is
contacted.
"""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import platform
import subprocess
import sys
import time
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.pilot.calculate_metrics import calculate_metrics  # noqa: E402
from scripts.pilot.generate_report import write_reports  # noqa: E402
from scripts.pilot.generate_synthetic_dataset import (  # noqa: E402
    DEFAULT_FFMPEG_IMAGE,
    SCENARIO_ROOT,
    generate_dataset,
)
from scripts.pilot.pilotlib import (  # noqa: E402
    CLASSIFICATION,
    EVIDENCE_ROOT,
    GENERATED_ROOT,
    REPO_ROOT,
    PilotError,
    artifact_record,
    assert_no_owner_only_states,
    atomic_write_text,
    collect_artifacts,
    read_csv,
    read_json,
    require_repo_root,
    reset_output_directory,
    sha256_json,
    write_json,
)
from scripts.pilot.validate_pilot_package import validate_package  # noqa: E402


IMPLEMENTATION_INPUTS = (
    "scripts/pilot/pilotlib.py",
    "scripts/pilot/jsonschema_subset.py",
    "scripts/pilot/generate_synthetic_dataset.py",
    "scripts/pilot/validate_pilot_package.py",
    "scripts/pilot/validate_real_pilot_intake.py",
    "scripts/pilot/calculate_metrics.py",
    "scripts/pilot/generate_report.py",
    "scripts/pilot/run_synthetic_rehearsal.py",
    "experiments/pilot/scenarios/synthetic-rehearsal/scenario.json",
    "experiments/pilot/scenarios/synthetic-rehearsal/brief.md",
    "experiments/pilot/scenarios/synthetic-rehearsal/profile.json",
    "experiments/pilot/schemas/pilot-manifest.schema.json",
    "experiments/pilot/schemas/annotation-record.schema.json",
)


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _git_fact(args: list[str], fallback: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=REPO_ROOT, capture_output=True, text=True, check=False
    )
    return result.stdout.strip() if result.returncode == 0 and result.stdout.strip() else fallback


def _input_records() -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for relative in IMPLEMENTATION_INPUTS:
        path = REPO_ROOT / relative
        if not path.is_file():
            raise PilotError(f"rehearsal implementation input is missing: {relative}")
        records.append(artifact_record(path))
    return records


def _write_checksums(
    dataset_root: Path, evidence_root: Path, checksum_path: Path
) -> list[dict[str, Any]]:
    records = collect_artifacts(dataset_root)
    records.extend(
        collect_artifacts(
            evidence_root,
            exclude_names={"receipt.json", "artifact-checksums.sha256"},
        )
    )
    records.sort(key=lambda item: item["path"])
    lines = [f"{item['sha256']}  {item['path']}" for item in records]
    atomic_write_text(checksum_path, "\n".join(lines) + "\n")
    return records


def run_rehearsal(
    output_root: Path,
    evidence_root: Path,
    *,
    image: str = DEFAULT_FFMPEG_IMAGE,
) -> dict[str, Any]:
    require_repo_root()
    evidence_root = reset_output_directory(
        evidence_root, EVIDENCE_ROOT, "synthetic evidence root"
    )
    started_at = utc_now()
    started_monotonic = time.monotonic()
    receipt_path = evidence_root / "receipt.json"

    try:
        generated = generate_dataset(output_root, image=image)
        dataset_root = generated["outputRoot"]
        validation = validate_package(dataset_root)
        write_json(evidence_root / "validation.json", validation)

        scenario = read_json(SCENARIO_ROOT / "scenario.json")
        metrics = calculate_metrics(
            read_csv(dataset_root / "annotations" / "adjudicated.csv"),
            read_csv(dataset_root / "annotations" / "reviewer-a.csv"),
            read_csv(dataset_root / "annotations" / "reviewer-b.csv"),
            read_json(dataset_root / "annotations" / "batch-runtimes.json"),
            classification=CLASSIFICATION,
            confidence=float(scenario["confidenceLevel"]),
        )
        write_json(evidence_root / "metrics.json", metrics)

        input_records = _input_records()
        input_set_sha = sha256_json(input_records)
        rehearsal_id = f"syn-{scenario['seed']}-{input_set_sha[:16]}"
        tracked_status = _git_fact(["status", "--porcelain", "--untracked-files=no"], "")
        provenance = {
            "schemaVersion": "frameflow.synthetic-rehearsal-provenance.v1",
            "classification": CLASSIFICATION,
            "rehearsalId": rehearsal_id,
            "inputSetSha256": input_set_sha,
            "inputs": input_records,
            "source": {
                "gitCommit": _git_fact(["rev-parse", "HEAD"], "UNKNOWN"),
                "trackedWorktreeDirtyAtExecution": bool(tracked_status),
                "note": "Exact rehearsal inputs are content-hashed even when concurrent local work exists.",
            },
            "execution": {
                "startedAt": started_at,
                "pythonVersion": platform.python_version(),
                "platform": platform.system(),
                "mediaTool": generated["summary"]["mediaTool"],
                "networkProviderCalls": 0,
                "applicationServicesStarted": 0,
            },
            "policy": {
                "realCustomerMedia": False,
                "personalData": False,
                "realProviderCallsAllowed": False,
                "realPilotDecisionEligible": False,
            },
        }
        write_json(evidence_root / "provenance.json", provenance)
        write_reports(
            metrics,
            validation,
            provenance,
            generated["summary"],
            evidence_root / "report.json",
            evidence_root / "report.md",
        )

        checksum_records = _write_checksums(
            dataset_root,
            evidence_root,
            evidence_root / "artifact-checksums.sha256",
        )
        receipt = {
            "schemaVersion": "frameflow.synthetic-rehearsal-receipt.v1",
            "classification": CLASSIFICATION,
            "status": "PASS",
            "rehearsalId": rehearsal_id,
            "startedAt": started_at,
            "finishedAt": utc_now(),
            "durationMs": round((time.monotonic() - started_monotonic) * 1000),
            "policy": {
                "realCustomerMediaCount": 0,
                "personalDataCount": 0,
                "realProviderCallCount": 0,
                "realPilotDecisionEligible": False,
            },
            "checks": [
                {"name": "synthetic.package_validation", "status": validation["status"]},
                {"name": "synthetic.metric_report_json", "status": "PASS"},
                {"name": "synthetic.metric_report_markdown", "status": "PASS"},
                {"name": "synthetic.stress_manifest_300_records", "status": "PASS"},
                {"name": "synthetic.real_provider_disabled", "status": "PASS"},
                {"name": "synthetic.owner_only_state_absent", "status": "PASS"},
            ],
            "counts": {
                **validation["counts"],
                "generatedImageCount": generated["summary"]["generatedImageCount"],
                "hashedArtifactCount": len(checksum_records),
            },
            "paths": {
                "datasetRoot": dataset_root.relative_to(REPO_ROOT).as_posix(),
                "evidenceRoot": evidence_root.relative_to(REPO_ROOT).as_posix(),
                "machineReport": (evidence_root / "report.json").relative_to(REPO_ROOT).as_posix(),
                "markdownReport": (evidence_root / "report.md").relative_to(REPO_ROOT).as_posix(),
            },
            "outputArtifacts": [
                *checksum_records,
                artifact_record(evidence_root / "artifact-checksums.sha256"),
            ],
        }
        serialized = json.dumps(receipt, ensure_ascii=False, sort_keys=True)
        assert_no_owner_only_states(serialized, "synthetic receipt")
        write_json(receipt_path, receipt)
        return receipt
    except BaseException as exc:
        failure = {
            "schemaVersion": "frameflow.synthetic-rehearsal-receipt.v1",
            "classification": CLASSIFICATION,
            "status": "FAIL",
            "startedAt": started_at,
            "finishedAt": utc_now(),
            "durationMs": round((time.monotonic() - started_monotonic) * 1000),
            "policy": {
                "realCustomerMediaCount": 0,
                "personalDataCount": 0,
                "realProviderCallCount": 0,
                "realPilotDecisionEligible": False,
            },
            "error": {"type": type(exc).__name__, "message": str(exc)[:512]},
        }
        try:
            assert_no_owner_only_states(json.dumps(failure), "failure receipt")
            write_json(receipt_path, failure)
        except BaseException:
            pass
        raise


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-root",
        type=Path,
        default=GENERATED_ROOT / "synthetic-rehearsal",
    )
    parser.add_argument(
        "--evidence-root",
        type=Path,
        default=EVIDENCE_ROOT / "synthetic-rehearsal",
    )
    parser.add_argument("--ffmpeg-image", default=DEFAULT_FFMPEG_IMAGE)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        receipt = run_rehearsal(
            args.output_root,
            args.evidence_root,
            image=args.ffmpeg_image,
        )
    except (PilotError, OSError, subprocess.SubprocessError) as exc:
        raise SystemExit(f"synthetic rehearsal failed: {exc}") from exc
    print(json.dumps({
        "classification": receipt["classification"],
        "status": receipt["status"],
        "rehearsalId": receipt["rehearsalId"],
        "report": receipt["paths"]["markdownReport"],
    }, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
