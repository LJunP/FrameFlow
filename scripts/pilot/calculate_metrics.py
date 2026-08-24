#!/usr/bin/env python3
"""Calculate F11 pilot metrics without inferring a value conclusion.

Reading order: ``main`` -> ``calculate_metrics`` -> ``proportion_metric`` ->
``wilson_interval`` / ``cohen_kappa``.  Inputs are human observations and two
independent blind-review exports; no application or Provider call occurs here.
"""

from __future__ import annotations

import argparse
from collections import Counter
from datetime import datetime
import math
from pathlib import Path
import random
from statistics import NormalDist
import sys
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.pilot.pilotlib import (
    CLASSIFICATION,
    PilotError,
    parse_optional_bool,
    read_csv,
    read_json,
    write_json,
)


QUALITY_LABELS = {"EXCELLENT", "ACCEPTABLE", "DEFECTIVE", "UNREVIEWABLE"}
MACHINE_DISPOSITIONS = {
    "ANALYZED",
    "AUTO_REJECT",
    "DUPLICATE_EXCLUDED",
    "REVIEW_REQUIRED",
    "ANALYSIS_ERROR",
    "INVALID",
}
REQUIRED_ANNOTATION_COLUMNS = {
    "candidate_id",
    "batch_id",
    "blind_review_id",
    "machine_disposition",
    "operational_full_watch",
    "baseline_full_watch_seconds",
    "assisted_review_seconds",
    "selected_for_delivery",
    "audit_included",
    "adjudicated_quality",
    "delivery_obvious_defect",
}
REQUIRED_REVIEW_COLUMNS = {"blind_review_id", "quality_label"}


def wilson_interval(successes: int, total: int, confidence: float = 0.95) -> dict[str, float] | None:
    """Return a two-sided Wilson score interval, or ``None`` for n=0."""
    if total == 0:
        return None
    if successes < 0 or successes > total:
        raise PilotError("proportion numerator must be between zero and denominator")
    if not 0 < confidence < 1:
        raise PilotError("confidence must be between zero and one")
    z = NormalDist().inv_cdf(0.5 + confidence / 2)
    observed = successes / total
    z2 = z * z
    denominator = 1 + z2 / total
    centre = (observed + z2 / (2 * total)) / denominator
    margin = z * math.sqrt((observed * (1 - observed) + z2 / (4 * total)) / total)
    return {
        "low": max(0.0, centre - margin / denominator),
        "high": min(1.0, centre + margin / denominator),
    }


def proportion_metric(
    *,
    numerator: int,
    denominator: int,
    missing_count: int,
    definition: str,
    confidence: float,
    limitations: list[str],
) -> dict[str, Any]:
    # ★ 核心：零分母必须输出 JSON null 和明确原因，禁止 NaN/Infinity；若这里
    # 直接相除，空批次会崩溃或把非标准数字写进报告，掩盖“没有证据”的事实。
    if denominator == 0:
        return {
            "definition": definition,
            "numerator": numerator,
            "denominator": denominator,
            "value": None,
            "confidenceInterval": None,
            "missingCount": missing_count,
            "complete": missing_count == 0,
            "sampleSizeCaution": True,
            "reason": "ZERO_DENOMINATOR",
            "limitations": limitations,
        }
    return {
        "definition": definition,
        "numerator": numerator,
        "denominator": denominator,
        "value": numerator / denominator,
        "confidenceInterval": {
            "method": "WILSON_SCORE_TWO_SIDED",
            "confidence": confidence,
            **(wilson_interval(numerator, denominator, confidence) or {}),
        },
        "missingCount": missing_count,
        "complete": missing_count == 0,
        "sampleSizeCaution": denominator < 30,
        "reason": None if missing_count == 0 else "OBSERVED_CASES_ONLY_MISSING_VALUES_PRESENT",
        "limitations": limitations,
    }


def _optional_seconds(value: str | None, label: str) -> float | None:
    normalized = (value or "").strip()
    if normalized == "":
        return None
    try:
        parsed = float(normalized)
    except ValueError as exc:
        raise PilotError(f"{label} must be a non-negative number or blank") from exc
    if not math.isfinite(parsed) or parsed < 0:
        raise PilotError(f"{label} must be a finite non-negative number or blank")
    return parsed


def _percentile(values: list[float], probability: float) -> float:
    ordered = sorted(values)
    position = (len(ordered) - 1) * probability
    low = math.floor(position)
    high = math.ceil(position)
    if low == high:
        return ordered[low]
    weight = position - low
    return ordered[low] * (1 - weight) + ordered[high] * weight


def time_savings_metric(
    pairs: list[tuple[float, float]],
    *,
    missing_count: int,
    confidence: float,
    bootstrap_seed: int = 2411,
    bootstrap_iterations: int = 5000,
) -> dict[str, Any]:
    baseline_total = sum(baseline for baseline, _assisted in pairs)
    assisted_total = sum(assisted for _baseline, assisted in pairs)
    if baseline_total == 0:
        return {
            "definition": "1 - sum(assisted operational review seconds) / sum(baseline full-watch seconds)",
            "baselineSeconds": baseline_total,
            "assistedSeconds": assisted_total,
            "savedSeconds": None,
            "candidateCount": len(pairs),
            "value": None,
            "confidenceInterval": None,
            "missingCount": missing_count,
            "complete": missing_count == 0,
            "sampleSizeCaution": True,
            "reason": "ZERO_BASELINE_SECONDS",
            "limitations": [
                "Blind-audit time is excluded from both baseline and assisted operational workload."
            ],
        }
    value = 1 - assisted_total / baseline_total
    bootstrap_values: list[float] = []
    if pairs:
        rng = random.Random(bootstrap_seed)
        for _ in range(bootstrap_iterations):
            sample = [pairs[rng.randrange(len(pairs))] for _item in pairs]
            sample_baseline = sum(item[0] for item in sample)
            if sample_baseline > 0:
                bootstrap_values.append(1 - sum(item[1] for item in sample) / sample_baseline)
    interval: dict[str, Any] | None = None
    if bootstrap_values:
        alpha = (1 - confidence) / 2
        interval = {
            "method": "PAIRED_CANDIDATE_BOOTSTRAP_PERCENTILE",
            "confidence": confidence,
            "iterations": bootstrap_iterations,
            "seed": bootstrap_seed,
            "low": _percentile(bootstrap_values, alpha),
            "high": _percentile(bootstrap_values, 1 - alpha),
        }
    # ★ 核心：主节省指标按秒聚合，并对候选成对重采样；若退回“未完整观看
    # 条数”，两秒与两分钟素材会被赋予同一权重，足以制造虚假的效率结论。
    return {
        "definition": "1 - sum(assisted operational review seconds) / sum(baseline full-watch seconds)",
        "baselineSeconds": baseline_total,
        "assistedSeconds": assisted_total,
        "savedSeconds": baseline_total - assisted_total,
        "candidateCount": len(pairs),
        "value": value,
        "confidenceInterval": interval,
        "missingCount": missing_count,
        "complete": missing_count == 0,
        "sampleSizeCaution": len(pairs) < 30,
        "reason": None if missing_count == 0 else "OBSERVED_PAIRS_ONLY_MISSING_VALUES_PRESENT",
        "limitations": [
            "Blind-audit time is excluded from both baseline and assisted operational workload.",
            "The paired bootstrap quantifies candidate sampling variation, not workflow measurement bias.",
            "Negative values are retained when assisted review takes longer than baseline.",
        ],
    }


def _require_columns(rows: list[dict[str, str]], required: set[str], label: str) -> None:
    if not rows:
        raise PilotError(f"{label} contains no rows")
    missing = required - set(rows[0])
    if missing:
        raise PilotError(f"{label} missing columns: {', '.join(sorted(missing))}")


def _validate_annotations(rows: list[dict[str, str]]) -> None:
    _require_columns(rows, REQUIRED_ANNOTATION_COLUMNS, "annotations")
    seen_candidates: set[str] = set()
    seen_blind_ids: set[str] = set()
    for index, row in enumerate(rows, start=2):
        candidate_id = row["candidate_id"].strip()
        blind_id = row["blind_review_id"].strip()
        if not candidate_id or candidate_id in seen_candidates:
            raise PilotError(f"annotations row {index} has blank/duplicate candidate_id")
        if not blind_id or blind_id in seen_blind_ids:
            raise PilotError(f"annotations row {index} has blank/duplicate blind_review_id")
        seen_candidates.add(candidate_id)
        seen_blind_ids.add(blind_id)
        disposition = row["machine_disposition"].strip()
        if disposition not in MACHINE_DISPOSITIONS:
            raise PilotError(f"annotations row {index} has unknown machine_disposition")
        quality = row["adjudicated_quality"].strip()
        if quality and quality not in QUALITY_LABELS:
            raise PilotError(f"annotations row {index} has unknown adjudicated_quality")
        for field in (
            "operational_full_watch",
            "selected_for_delivery",
            "audit_included",
            "delivery_obvious_defect",
        ):
            parse_optional_bool(row[field], f"annotations row {index} {field}")
        _optional_seconds(
            row["baseline_full_watch_seconds"],
            f"annotations row {index} baseline_full_watch_seconds",
        )
        _optional_seconds(
            row["assisted_review_seconds"],
            f"annotations row {index} assisted_review_seconds",
        )


def _review_map(rows: list[dict[str, str]], label: str) -> tuple[dict[str, str], int]:
    _require_columns(rows, REQUIRED_REVIEW_COLUMNS, label)
    result: dict[str, str] = {}
    missing = 0
    for index, row in enumerate(rows, start=2):
        blind_id = row["blind_review_id"].strip()
        if not blind_id or blind_id in result:
            raise PilotError(f"{label} row {index} has blank/duplicate blind_review_id")
        quality = row["quality_label"].strip()
        if quality == "":
            missing += 1
        elif quality not in QUALITY_LABELS:
            raise PilotError(f"{label} row {index} has unknown quality_label")
        result[blind_id] = quality
    return result, missing


def cohen_kappa(
    reviewer_a: dict[str, str], reviewer_b: dict[str, str]
) -> dict[str, Any]:
    shared_ids = sorted(set(reviewer_a) & set(reviewer_b))
    comparable = [
        blind_id
        for blind_id in shared_ids
        if reviewer_a[blind_id] and reviewer_b[blind_id]
    ]
    missing_or_unpaired = len(set(reviewer_a) | set(reviewer_b)) - len(comparable)
    if not comparable:
        return {
            "overlapCount": 0,
            "rawAgreement": None,
            "cohenKappa": None,
            "missingOrUnpairedCount": missing_or_unpaired,
            "sampleSizeCaution": True,
            "reason": "ZERO_COMPARABLE_LABELS",
        }
    agreements = sum(reviewer_a[item] == reviewer_b[item] for item in comparable)
    counts_a = Counter(reviewer_a[item] for item in comparable)
    counts_b = Counter(reviewer_b[item] for item in comparable)
    observed = agreements / len(comparable)
    expected = sum(
        (counts_a[category] / len(comparable)) * (counts_b[category] / len(comparable))
        for category in QUALITY_LABELS
    )
    if math.isclose(expected, 1.0):
        kappa = None
        reason = "NO_LABEL_VARIANCE"
    else:
        kappa = (observed - expected) / (1 - expected)
        reason = None
    return {
        "overlapCount": len(comparable),
        "rawAgreement": observed,
        "cohenKappa": kappa,
        "missingOrUnpairedCount": missing_or_unpaired,
        "sampleSizeCaution": len(comparable) < 30,
        "reason": reason,
    }


def _parse_instant(value: str, label: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise PilotError(f"{label} is not an ISO-8601 timestamp") from exc
    if parsed.tzinfo is None:
        raise PilotError(f"{label} must include a timezone")
    return parsed


def processing_time_metric(runtime_data: dict[str, Any]) -> dict[str, Any]:
    raw_batches = runtime_data.get("batches")
    if not isinstance(raw_batches, list):
        raise PilotError("batch runtimes must contain a batches array")
    durations: list[float] = []
    missing = 0
    for index, raw in enumerate(raw_batches):
        if not isinstance(raw, dict):
            raise PilotError(f"batch runtimes item {index} must be an object")
        start = raw.get("uploadCompletedAt")
        end = raw.get("rankingReadyAt")
        if not isinstance(start, str) or not start or not isinstance(end, str) or not end:
            missing += 1
            continue
        duration = (_parse_instant(end, f"batch[{index}].rankingReadyAt") - _parse_instant(
            start, f"batch[{index}].uploadCompletedAt"
        )).total_seconds()
        if duration < 0:
            raise PilotError(f"batch runtimes item {index} ends before it starts")
        durations.append(duration)
    if not durations:
        return {
            "definition": "rankingReadyAt - uploadCompletedAt, in seconds",
            "observedBatchCount": 0,
            "missingBatchCount": missing,
            "minSeconds": None,
            "medianSeconds": None,
            "maxSeconds": None,
            "reason": "ZERO_OBSERVED_BATCHES",
        }
    ordered = sorted(durations)
    middle = len(ordered) // 2
    median = (
        ordered[middle]
        if len(ordered) % 2 == 1
        else (ordered[middle - 1] + ordered[middle]) / 2
    )
    return {
        "definition": "rankingReadyAt - uploadCompletedAt, in seconds",
        "observedBatchCount": len(ordered),
        "missingBatchCount": missing,
        "minSeconds": ordered[0],
        "medianSeconds": median,
        "maxSeconds": ordered[-1],
        "reason": None if missing == 0 else "MISSING_BATCH_TIMESTAMPS_EXCLUDED",
    }


def calculate_metrics(
    annotations: list[dict[str, str]],
    reviewer_a_rows: list[dict[str, str]],
    reviewer_b_rows: list[dict[str, str]],
    runtime_data: dict[str, Any],
    *,
    classification: str = CLASSIFICATION,
    confidence: float = 0.95,
) -> dict[str, Any]:
    if classification not in {CLASSIFICATION, "REAL_PILOT"}:
        raise PilotError("classification must be SYNTHETIC_REHEARSAL or REAL_PILOT")
    if not 0 < confidence < 1:
        raise PilotError("confidence must be between zero and one")
    _validate_annotations(annotations)
    reviewer_a, reviewer_a_missing = _review_map(reviewer_a_rows, "reviewer A")
    reviewer_b, reviewer_b_missing = _review_map(reviewer_b_rows, "reviewer B")

    operational_watch_values = [
        parse_optional_bool(row["operational_full_watch"], "operational_full_watch")
        for row in annotations
    ]
    known_watch = [value for value in operational_watch_values if value is not None]
    saved_count = sum(value is False for value in known_watch)
    watch_missing = len(annotations) - len(known_watch)
    full_watch_avoidance = proportion_metric(
        numerator=saved_count,
        denominator=len(known_watch),
        missing_count=watch_missing,
        definition=(
            "candidates without an operational full watch / candidates with observed operational-watch data; "
            "blind audit watches are excluded from operational workload"
        ),
        confidence=confidence,
        limitations=["Missing operational-watch values are excluded and reported separately."],
    )

    time_pairs: list[tuple[float, float]] = []
    time_missing = 0
    for row in annotations:
        baseline = _optional_seconds(
            row["baseline_full_watch_seconds"], "baseline_full_watch_seconds"
        )
        assisted = _optional_seconds(row["assisted_review_seconds"], "assisted_review_seconds")
        if baseline is None or assisted is None:
            time_missing += 1
        else:
            time_pairs.append((baseline, assisted))
    viewing_time_savings = time_savings_metric(
        time_pairs,
        missing_count=time_missing,
        confidence=confidence,
    )

    auto_rejects = [row for row in annotations if row["machine_disposition"] == "AUTO_REJECT"]
    audited_rejects: list[dict[str, str]] = []
    false_kill_missing = 0
    for row in auto_rejects:
        included = parse_optional_bool(row["audit_included"], "audit_included")
        quality = row["adjudicated_quality"].strip()
        if included is True and quality in QUALITY_LABELS:
            audited_rejects.append(row)
        elif included is True:
            false_kill_missing += 1
    false_kills = sum(
        row["adjudicated_quality"].strip() in {"EXCELLENT", "ACCEPTABLE"}
        for row in audited_rejects
    )
    false_kill_rate = proportion_metric(
        numerator=false_kills,
        denominator=len(audited_rejects),
        missing_count=false_kill_missing,
        definition="adjudicated EXCELLENT or ACCEPTABLE among audited AUTO_REJECT candidates",
        confidence=confidence,
        limitations=[
            "Duplicate exclusions are not quality rejections and are excluded.",
            "The estimate represents the population only for a census or pre-registered equal-probability sample.",
        ],
    )

    delivered = [
        row
        for row in annotations
        if parse_optional_bool(row["selected_for_delivery"], "selected_for_delivery") is True
    ]
    observed_delivery_defects: list[bool] = []
    miss_missing = 0
    for row in delivered:
        defect = parse_optional_bool(row["delivery_obvious_defect"], "delivery_obvious_defect")
        if defect is None:
            miss_missing += 1
        else:
            observed_delivery_defects.append(defect)
    miss_rate = proportion_metric(
        numerator=sum(observed_delivery_defects),
        denominator=len(observed_delivery_defects),
        missing_count=miss_missing,
        definition="delivered candidates with an adjudicated obvious defect / observed delivered candidates",
        confidence=confidence,
        limitations=["Post-delivery defects require the pre-registered defect taxonomy and adjudication."],
    )

    agreement = cohen_kappa(reviewer_a, reviewer_b)
    agreement["reviewerAMissingCount"] = reviewer_a_missing
    agreement["reviewerBMissingCount"] = reviewer_b_missing
    agreement["definition"] = "Cohen's kappa and raw agreement on independent blind quality labels"

    # ★ 核心：指标层只描述数据，永远不把阈值比较升级为项目/试点结论；
    # 否则一次合成输入或小样本就可能越过所有者的真实价值审批门禁。
    return {
        "schemaVersion": "frameflow.pilot-metrics.v1",
        "classification": classification,
        "status": "DESCRIPTIVE_METRICS_ONLY",
        "valueConclusionAuthorized": False,
        "scope": {
            "candidateCount": len(annotations),
            "batchCount": len({row["batch_id"] for row in annotations}),
            "confidenceLevel": confidence,
        },
        "metrics": {
            "manualViewingTimeSavingsRate": viewing_time_savings,
            "fullWatchAvoidanceRate": full_watch_avoidance,
            "falseKillRate": false_kill_rate,
            "missRate": miss_rate,
            "batchProcessingTime": processing_time_metric(runtime_data),
            "blindReviewAgreement": agreement,
        },
        "sampleLimitations": [
            "Any proportion denominator below 30 is flagged and is not a stable population estimate.",
            "Confidence intervals quantify sampling uncertainty only; they do not correct selection bias or label error.",
            "Missing values are never imputed; observed-only estimates expose their missing counts.",
            "Synthetic rehearsal values validate calculations, not customer behaviour or model quality.",
        ],
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--annotations", type=Path, required=True)
    parser.add_argument("--reviewer-a", type=Path, required=True)
    parser.add_argument("--reviewer-b", type=Path, required=True)
    parser.add_argument("--batch-runtimes", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--classification",
        choices=[CLASSIFICATION, "REAL_PILOT"],
        default=CLASSIFICATION,
    )
    parser.add_argument("--confidence", type=float, default=0.95)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if not 0 < args.confidence < 1:
        raise SystemExit("--confidence must be between zero and one")
    try:
        metrics = calculate_metrics(
            read_csv(args.annotations),
            read_csv(args.reviewer_a),
            read_csv(args.reviewer_b),
            read_json(args.batch_runtimes),
            classification=args.classification,
            confidence=args.confidence,
        )
        write_json(args.output, metrics)
    except PilotError as exc:
        raise SystemExit(f"pilot metrics failed: {exc}") from exc
    print(args.output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
