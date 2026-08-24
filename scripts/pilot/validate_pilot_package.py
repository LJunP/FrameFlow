#!/usr/bin/env python3
"""Validate an F11 pilot package with synthetic fail-closed invariants.

Reading order: ``main`` -> ``validate_package`` -> ``require_check``.  The JSON
Schemas are portable contracts; this validator additionally verifies bytes,
hashes, blind-packet isolation, and synthetic/Provider policy at runtime.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path
import sys
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.pilot.pilotlib import (  # noqa: E402
    CLASSIFICATION,
    GENERATED_ROOT,
    PILOT_ROOT,
    PilotError,
    ensure_descendant,
    parse_optional_bool,
    read_csv,
    read_json,
    sha256_file,
    write_json,
)
from scripts.pilot.jsonschema_subset import validate_instance  # noqa: E402


def _file_path(dataset_root: Path, reference: Any, label: str) -> Path:
    if not isinstance(reference, dict):
        raise PilotError(f"{label} must be a file-reference object")
    relative = reference.get("path")
    if not isinstance(relative, str) or not relative or Path(relative).is_absolute():
        raise PilotError(f"{label}.path must be a non-empty relative path")
    path = (dataset_root / relative).resolve(strict=False)
    try:
        path.relative_to(dataset_root.resolve(strict=True))
    except ValueError as exc:
        raise PilotError(f"{label}.path escapes dataset root") from exc
    if path.is_symlink() or not path.is_file():
        raise PilotError(f"{label}.path is missing or a symlink")
    if reference.get("sizeBytes") != path.stat().st_size:
        raise PilotError(f"{label}.sizeBytes does not match bytes on disk")
    if reference.get("sha256") != sha256_file(path):
        raise PilotError(f"{label}.sha256 does not match bytes on disk")
    return path


def validate_package(dataset_root: Path) -> dict[str, Any]:
    dataset_root = ensure_descendant(dataset_root, GENERATED_ROOT, "dataset root")
    checks: list[dict[str, Any]] = []

    def require_check(name: str, condition: bool, details: dict[str, Any] | None = None) -> None:
        entry: dict[str, Any] = {"name": name, "status": "PASS" if condition else "FAIL"}
        if details:
            entry["details"] = details
        checks.append(entry)
        if not condition:
            raise PilotError(f"package check failed: {name}")

    schema_path = PILOT_ROOT / "schemas" / "pilot-manifest.schema.json"
    annotation_schema_path = PILOT_ROOT / "schemas" / "annotation-record.schema.json"
    schema = read_json(schema_path)
    annotation_schema = read_json(annotation_schema_path)
    require_check("schema.pilot_manifest.draft_2020_12", schema.get("$schema", "").endswith("2020-12/schema"))
    require_check("schema.annotation.draft_2020_12", annotation_schema.get("$schema", "").endswith("2020-12/schema"))

    manifest = read_json(dataset_root / "manifest.json")
    if not isinstance(manifest, dict):
        raise PilotError("manifest must be an object")
    manifest_schema_errors = validate_instance(manifest, schema)
    require_check(
        "schema.pilot_manifest.runtime_validation",
        not manifest_schema_errors,
        {"firstError": manifest_schema_errors[0]} if manifest_schema_errors else None,
    )
    require_check("manifest.schema_version", manifest.get("schemaVersion") == "frameflow.pilot-manifest.v1")
    require_check("manifest.classification", manifest.get("classification") == CLASSIFICATION)
    data_policy = manifest.get("dataPolicy")
    provider = manifest.get("providerExecution")
    require_check(
        "policy.no_real_customer_media",
        isinstance(data_policy, dict)
        and data_policy.get("containsRealCustomerMedia") is False
        and data_policy.get("containsPersonalData") is False
        and data_policy.get("rightsBasis") == "SYNTHETIC_GENERATED"
        and data_policy.get("retentionClass") == "REGENERABLE",
    )
    require_check(
        "policy.no_real_provider",
        isinstance(provider, dict)
        and provider.get("mode") in {"DISABLED", "FAKE"}
        and provider.get("realCallsAllowed") is False
        and provider.get("callEvidenceReferences") == [],
    )

    brief_path = _file_path(dataset_root, manifest.get("brief"), "brief")
    profile_path = _file_path(dataset_root, manifest.get("profile"), "profile")
    require_check("brief.synthetic_banner", CLASSIFICATION in brief_path.read_text(encoding="utf-8"))
    profile = read_json(profile_path)
    require_check(
        "profile.semantic_disabled",
        isinstance(profile, dict)
        and isinstance(profile.get("semantic"), dict)
        and profile["semantic"].get("enabled") is False,
    )

    batches = manifest.get("batches")
    if not isinstance(batches, list) or not batches:
        raise PilotError("manifest.batches must be a non-empty array")
    seen_batch_ids: set[str] = set()
    seen_candidate_ids: set[str] = set()
    metric_candidates: set[str] = set()
    materialized_media: list[dict[str, Any]] = []
    stress_batch_count = 0
    for batch_index, batch in enumerate(batches):
        if not isinstance(batch, dict):
            raise PilotError(f"batch {batch_index} must be an object")
        batch_id = batch.get("batchId")
        candidates = batch.get("candidates")
        if not isinstance(batch_id, str) or not batch_id or batch_id in seen_batch_ids:
            raise PilotError(f"batch {batch_index} has blank/duplicate batchId")
        seen_batch_ids.add(batch_id)
        if not isinstance(candidates, list) or not 1 <= len(candidates) <= 300:
            raise PilotError(f"batch {batch_id} must contain 1..300 candidates")
        require_check(
            f"batch.{batch_id}.capacity_matches",
            batch.get("capacity") == len(candidates),
            {"candidateCount": len(candidates)},
        )
        if batch.get("purpose") == "MANIFEST_STRESS":
            stress_batch_count += len(candidates)
            require_check(f"batch.{batch_id}.not_metric_scope", batch.get("metricScope") is False)
        if batch.get("metricScope") is True:
            metric_candidates.update(
                str(candidate.get("candidateId")) for candidate in candidates if isinstance(candidate, dict)
            )
        for candidate_index, candidate in enumerate(candidates):
            if not isinstance(candidate, dict):
                raise PilotError(f"candidate {batch_id}[{candidate_index}] must be an object")
            candidate_id = candidate.get("candidateId")
            if not isinstance(candidate_id, str) or not candidate_id or candidate_id in seen_candidate_ids:
                raise PilotError(f"candidate {batch_id}[{candidate_index}] has blank/duplicate id")
            seen_candidate_ids.add(candidate_id)
            require_check(
                f"candidate.{candidate_id}.data_classification",
                candidate.get("containsCustomerData") is False
                and candidate.get("containsPersonalData") is False,
            )
            media_path = _file_path(dataset_root, candidate.get("media"), f"candidate.{candidate_id}.media")
            if candidate.get("materialization") == "MATERIALIZED":
                materialized_media.append({"candidateId": candidate_id, "path": media_path})

    require_check("stress.maximum_capacity_records", stress_batch_count == 300)
    require_check("metric.fixture_candidate_count", len(metric_candidates) == 10)

    annotations = read_csv(dataset_root / "annotations" / "adjudicated.csv")
    if not annotations:
        raise PilotError("annotations must contain at least one row")
    annotation_required_columns = set(annotation_schema.get("required", []))
    missing_annotation_columns = annotation_required_columns - set(annotations[0])
    require_check(
        "schema.annotation.required_columns",
        not missing_annotation_columns,
        {"missing": sorted(missing_annotation_columns)} if missing_annotation_columns else None,
    )
    annotation_candidates = {row.get("candidate_id", "").strip() for row in annotations}
    require_check("annotations.metric_scope_exact", annotation_candidates == metric_candidates)
    blind_ids = {row.get("blind_review_id", "").strip() for row in annotations}
    require_check("annotations.unique_blind_ids", "" not in blind_ids and len(blind_ids) == len(annotations))
    for index, row in enumerate(annotations, start=2):
        for field in (
            "operational_full_watch",
            "selected_for_delivery",
            "audit_included",
            "delivery_obvious_defect",
        ):
            parse_optional_bool(row.get(field), f"annotation row {index} {field}")
        typed_row: dict[str, Any] = dict(row)
        for field in (
            "operational_full_watch",
            "selected_for_delivery",
            "audit_included",
            "delivery_obvious_defect",
        ):
            typed_row[field] = parse_optional_bool(row.get(field), f"annotation row {index} {field}")
        for field in ("baseline_full_watch_seconds", "assisted_review_seconds"):
            raw_value = (row.get(field) or "").strip()
            if raw_value == "":
                typed_row[field] = None
            else:
                try:
                    numeric = float(raw_value)
                except ValueError as exc:
                    raise PilotError(f"annotation row {index} {field} must be numeric") from exc
                if not math.isfinite(numeric):
                    raise PilotError(f"annotation row {index} {field} must be finite")
                typed_row[field] = numeric
        if typed_row.get("adjudicated_quality") == "":
            typed_row["adjudicated_quality"] = None
        annotation_errors = validate_instance(typed_row, annotation_schema)
        if annotation_errors:
            raise PilotError(
                f"annotation row {index} violates annotation Schema: {annotation_errors[0]}"
            )
    require_check("schema.annotation.runtime_validation", True, {"rowCount": len(annotations)})

    for reviewer_name in ("reviewer-a.csv", "reviewer-b.csv"):
        reviewer_rows = read_csv(dataset_root / "annotations" / reviewer_name)
        reviewer_blind_ids = {row.get("blind_review_id", "").strip() for row in reviewer_rows}
        require_check(f"blind_packet.{reviewer_name}.scope_exact", reviewer_blind_ids == blind_ids)
        # ★ 核心：盲评包不得出现候选 ID 或机器判定字段；泄露后的一致率与标签
        # 都会受锚定偏差污染，即使统计脚本仍能算出数字也不再可信。
        require_check(
            f"blind_packet.{reviewer_name}.machine_output_hidden",
            all(
                forbidden not in reviewer_rows[0]
                for forbidden in ("candidate_id", "machine_disposition", "expected_anomalies")
            ),
        )

    stress_rows = read_csv(dataset_root / "stress" / "candidates-300.csv")
    require_check(
        "stress.csv.unique_300",
        len(stress_rows) == 300
        and len({row.get("candidate_id") for row in stress_rows}) == 300,
    )
    probes = read_json(dataset_root / "media-probes.json")
    require_check(
        "media.invalid_container_probe_fails",
        isinstance(probes, dict)
        and probes.get("invalid_container", {}).get("parseSucceeded") is False
        and probes.get("truncated_container", {}).get("parseSucceeded") is False,
    )
    require_check(
        "media.valid_fixture_probes_succeed",
        isinstance(probes, dict)
        and all(
            probes.get(key, {}).get("parseSucceeded") is True
            for key in (
                "clean_a",
                "clean_a_copy",
                "clean_b",
                "too_short",
                "black",
                "frozen",
                "silent",
                "low_resolution",
            )
        ),
    )
    image_paths = sorted((dataset_root / "images").glob("*.png"))
    require_check(
        "images.four_valid_png_signatures",
        len(image_paths) == 4 and all(path.read_bytes().startswith(b"\x89PNG\r\n\x1a\n") for path in image_paths),
    )
    clean = next(item for item in materialized_media if item["candidateId"] == "SYN-C-001")
    duplicate = next(item for item in materialized_media if item["candidateId"] == "SYN-C-002")
    require_check("media.exact_duplicate_bytes", sha256_file(clean["path"]) == sha256_file(duplicate["path"]))

    return {
        "schemaVersion": "frameflow.pilot-package-validation.v1",
        "classification": CLASSIFICATION,
        "status": "PASS",
        "validator": "SCHEMA_DRIVEN_DRAFT_2020_12_SUBSET_PLUS_INVARIANTS",
        "schemaEngineLimitations": [
            "The bundled engine executes every keyword currently present in the committed Schemas and rejects unsupported new keywords.",
            "It is not a full JSON Schema conformance implementation; external production tooling should also validate with a certified Draft 2020-12 engine.",
        ],
        "portableSchemas": [
            "experiments/pilot/schemas/pilot-manifest.schema.json",
            "experiments/pilot/schemas/annotation-record.schema.json",
        ],
        "counts": {
            "batchCount": len(batches),
            "candidateRecordCount": len(seen_candidate_ids),
            "metricCandidateCount": len(metric_candidates),
            "stressCandidateCount": stress_batch_count,
            "materializedMediaCandidateCount": len(materialized_media),
            "checkCount": len(checks),
        },
        "checks": checks,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = validate_package(args.dataset_root)
        write_json(args.output, result)
    except PilotError as exc:
        raise SystemExit(f"pilot package validation failed: {exc}") from exc
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
