#!/usr/bin/env python3
"""Future operator tool: validate an explicitly authorized real-pilot intake.

This command is deliberately not part of the synthetic rehearsal.  It reads a
user-supplied dataset outside the Git repository, verifies consent/retention,
Provider approval evidence and media hashes, and writes only a redacted receipt.
It never invokes the application or a Provider.
"""

from __future__ import annotations

import argparse
from datetime import datetime
import hashlib
from pathlib import Path
import sys
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.pilot.pilotlib import (  # noqa: E402
    PILOT_ROOT,
    REPO_ROOT,
    PilotError,
    read_json,
    sha256_file,
    write_json,
)
from scripts.pilot.jsonschema_subset import validate_instance  # noqa: E402


def _instant(value: Any, label: str) -> datetime:
    if not isinstance(value, str) or not value:
        raise PilotError(f"{label} must be a non-empty ISO-8601 timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise PilotError(f"{label} must be an ISO-8601 timestamp") from exc
    if parsed.tzinfo is None:
        raise PilotError(f"{label} must include a timezone")
    return parsed


def _external_root(path: Path) -> Path:
    resolved = path.expanduser().resolve(strict=True)
    if not resolved.is_dir() or path.is_symlink():
        raise PilotError("dataset root must be a real non-symlink directory")
    try:
        resolved.relative_to(REPO_ROOT.resolve(strict=True))
    except ValueError:
        return resolved
    raise PilotError("real pilot data must stay outside the Git repository")


def _referenced_file(root: Path, raw: Any, label: str) -> Path:
    reference = raw if isinstance(raw, dict) else {"path": raw}
    relative = reference.get("path")
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise PilotError(f"{label} path must be non-empty and relative")
    path = (root / relative).resolve(strict=False)
    try:
        path.relative_to(root)
    except ValueError as exc:
        raise PilotError(f"{label} escapes dataset root") from exc
    if path.is_symlink() or not path.is_file():
        raise PilotError(f"{label} is missing or a symlink")
    if "sizeBytes" in reference and reference["sizeBytes"] != path.stat().st_size:
        raise PilotError(f"{label} byte length mismatch")
    if "sha256" in reference and reference["sha256"] != sha256_file(path):
        raise PilotError(f"{label} SHA-256 mismatch")
    return path


def validate_real_intake(dataset_root: Path) -> dict[str, Any]:
    root = _external_root(dataset_root)
    manifest_path = root / "manifest.json"
    manifest = read_json(manifest_path)
    if not isinstance(manifest, dict):
        raise PilotError("manifest must be a JSON object")
    schema = read_json(PILOT_ROOT / "schemas" / "pilot-manifest.schema.json")
    schema_errors = validate_instance(manifest, schema)
    if schema_errors:
        raise PilotError(f"manifest violates pilot JSON Schema: {schema_errors[0]}")
    if manifest.get("schemaVersion") != "frameflow.pilot-manifest.v1":
        raise PilotError("unsupported manifest schemaVersion")
    if manifest.get("classification") != "REAL_PILOT":
        raise PilotError("real intake manifest classification must be REAL_PILOT")

    policy = manifest.get("dataPolicy")
    provider = manifest.get("providerExecution")
    if not isinstance(policy, dict) or not isinstance(provider, dict):
        raise PilotError("manifest dataPolicy/providerExecution must be objects")
    if not (
        policy.get("containsRealCustomerMedia") is True
        and policy.get("rightsBasis") == "OWNER_CONFIRMED_RIGHTS"
        and policy.get("retentionClass") == "PILOT_RESTRICTED"
        and isinstance(policy.get("approvalReference"), str)
        and policy["approvalReference"].strip()
    ):
        raise PilotError("real intake lacks confirmed rights, restricted retention, or approval reference")
    created = _instant(manifest.get("createdAt"), "createdAt")
    deletion_due = _instant(policy.get("deletionDueAt"), "dataPolicy.deletionDueAt")
    if deletion_due <= created:
        raise PilotError("dataPolicy.deletionDueAt must be after manifest.createdAt")
    if not (
        provider.get("mode") == "OWNER_APPROVED_REAL"
        and provider.get("realCallsAllowed") is True
        and isinstance(provider.get("callEvidenceReferences"), list)
        and provider["callEvidenceReferences"]
    ):
        raise PilotError("real intake lacks explicit real-Provider approval/call evidence references")

    _referenced_file(root, manifest.get("brief"), "brief")
    _referenced_file(root, manifest.get("profile"), "profile")
    for index, reference in enumerate(provider["callEvidenceReferences"]):
        _referenced_file(root, reference, f"Provider evidence {index}")

    batches = manifest.get("batches")
    if not isinstance(batches, list) or not batches:
        raise PilotError("real intake requires at least one batch")
    seen: set[str] = set()
    batch_count = 0
    candidate_count = 0
    media_bytes = 0
    for batch_index, batch in enumerate(batches):
        if not isinstance(batch, dict):
            raise PilotError(f"batch {batch_index} must be an object")
        candidates = batch.get("candidates")
        if not isinstance(candidates, list) or not 1 <= len(candidates) <= 300:
            raise PilotError(f"batch {batch_index} must contain 1..300 candidates")
        if batch.get("capacity") != len(candidates):
            raise PilotError(f"batch {batch_index} capacity does not match candidate count")
        batch_count += 1
        for candidate_index, candidate in enumerate(candidates):
            if not isinstance(candidate, dict):
                raise PilotError(f"candidate {batch_index}/{candidate_index} must be an object")
            candidate_id = candidate.get("candidateId")
            if not isinstance(candidate_id, str) or not candidate_id or candidate_id in seen:
                raise PilotError("candidate IDs must be non-empty and globally unique")
            seen.add(candidate_id)
            media = _referenced_file(
                root, candidate.get("media"), f"candidate {batch_index}/{candidate_index} media"
            )
            media_bytes += media.stat().st_size
            candidate_count += 1

    # ★ 核心：receipt 只保留聚合数、清单哈希和审批引用哈希，不回显客户名、
    # candidate ID、媒体路径或 Provider 配置；否则校验工具会成为新的敏感数据泄露面。
    return {
        "schemaVersion": "frameflow.real-pilot-intake-validation.v1",
        "classification": "REAL_PILOT",
        "status": "PASS",
        "descriptiveReportOnly": True,
        "manifestSha256": sha256_file(manifest_path),
        "approvalReferenceSha256": hashlib.sha256(
            policy["approvalReference"].encode("utf-8")
        ).hexdigest(),
        "counts": {
            "batchCount": batch_count,
            "candidateCount": candidate_count,
            "mediaBytesVerified": media_bytes,
            "providerEvidenceReferenceCount": len(provider["callEvidenceReferences"]),
        },
        "retention": {"deletionDueAt": policy["deletionDueAt"]},
        "checks": [
            "DATA_OUTSIDE_REPOSITORY",
            "RIGHTS_AND_APPROVAL_PRESENT",
            "RETENTION_DEADLINE_PRESENT",
            "MEDIA_HASHES_MATCH",
            "REAL_PROVIDER_APPROVAL_EVIDENCE_PRESENT",
        ],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--confirm-authorized-intake",
        action="store_true",
        help="required acknowledgement that rights and real-Provider approval already exist",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if not args.confirm_authorized_intake:
        raise SystemExit("refusing real intake without --confirm-authorized-intake")
    try:
        root = _external_root(args.dataset_root)
        output = args.output.expanduser().resolve(strict=False)
        try:
            output.relative_to(root)
        except ValueError as exc:
            raise PilotError("real intake validation output must remain inside dataset root") from exc
        result = validate_real_intake(root)
        write_json(output, result)
    except PilotError as exc:
        raise SystemExit(f"real pilot intake validation failed: {exc}") from exc
    print("real pilot intake validation PASS; receipt kept inside authorized dataset root")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
