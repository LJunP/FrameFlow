#!/usr/bin/env python3
"""Generate the F11 synthetic pilot dataset and 300-record stress manifest.

Reading order: ``main`` -> ``generate_dataset`` -> ``resolve_media_runner`` ->
``MediaRunner.generate_video`` / ``probe`` -> manifest and annotation writers.
The Docker path is network-disabled and uses a pre-existing FFmpeg image only.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
import json
from pathlib import Path
import random
import shutil
import subprocess
import sys
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.pilot.pilotlib import (  # noqa: E402
    CLASSIFICATION,
    GENERATED_ROOT,
    PILOT_ROOT,
    PilotError,
    read_json,
    require_repo_root,
    reset_output_directory,
    sha256_file,
    write_csv,
    write_json,
)


DEFAULT_FFMPEG_IMAGE = (
    "linuxserver/ffmpeg@sha256:"
    "771895205f3a62023f14e5ca1fe94be8007ecaf8a7c268d9c502de260c213d22"
)
SCENARIO_ROOT = PILOT_ROOT / "scenarios" / "synthetic-rehearsal"


@dataclass(frozen=True, slots=True)
class MediaRecipe:
    key: str
    file_name: str
    video_source: str | None
    duration: float | None
    audio_hz: int | None
    anomalies: tuple[str, ...]
    duplicate_of: str | None = None
    truncate_from: str | None = None


RECIPES = (
    MediaRecipe(
        "clean_a", "clean-motion-a.mp4", "testsrc2=size=320x180:rate=24:duration=2", 2, 440, ()
    ),
    MediaRecipe(
        "clean_a_copy",
        "clean-motion-a-exact-copy.mp4",
        None,
        None,
        None,
        ("EXACT_DUPLICATE",),
        duplicate_of="clean_a",
    ),
    MediaRecipe(
        "clean_b",
        "clean-motion-b.mp4",
        "smptebars=size=320x180:rate=24:duration=2",
        2,
        660,
        (),
    ),
    MediaRecipe(
        "too_short",
        "too-short.mp4",
        "testsrc2=size=320x180:rate=24:duration=0.5",
        0.5,
        880,
        ("TOO_SHORT",),
    ),
    MediaRecipe(
        "black",
        "black-frames.mp4",
        "color=c=black:size=320x180:rate=24:duration=2",
        2,
        330,
        ("BLACK_FRAMES",),
    ),
    MediaRecipe(
        "frozen",
        "frozen-video.mp4",
        "color=c=0x36648B:size=320x180:rate=24:duration=2",
        2,
        550,
        ("FROZEN_VIDEO",),
    ),
    MediaRecipe(
        "silent",
        "silent-video.mp4",
        "testsrc2=size=320x180:rate=24:duration=2",
        2,
        None,
        ("NO_AUDIO",),
    ),
    MediaRecipe(
        "low_resolution",
        "low-resolution.mp4",
        "testsrc2=size=128x72:rate=24:duration=2",
        2,
        770,
        ("LOW_RESOLUTION",),
    ),
    MediaRecipe(
        "invalid_container", "invalid-container.mp4", None, None, None, ("INVALID_CONTAINER",)
    ),
    MediaRecipe(
        "truncated_container",
        "truncated-container.mp4",
        None,
        None,
        None,
        ("TRUNCATED_CONTAINER",),
        truncate_from="clean_a",
    ),
)


CORE_CASES: tuple[dict[str, Any], ...] = (
    {
        "media": "clean_a",
        "disposition": "ANALYZED",
        "watched": True,
        "baselineSeconds": 2.0,
        "assistedSeconds": 2.0,
        "delivered": True,
        "quality": "EXCELLENT",
        "deliveryDefect": False,
        "reviewA": "EXCELLENT",
        "reviewB": "EXCELLENT",
    },
    {
        "media": "clean_a_copy",
        "disposition": "DUPLICATE_EXCLUDED",
        "watched": False,
        "baselineSeconds": 2.0,
        "assistedSeconds": 0.1,
        "delivered": False,
        "quality": "EXCELLENT",
        "deliveryDefect": None,
        "reviewA": "EXCELLENT",
        "reviewB": "EXCELLENT",
    },
    {
        "media": "clean_b",
        "disposition": "ANALYZED",
        "watched": True,
        "baselineSeconds": 2.0,
        "assistedSeconds": 2.0,
        "delivered": True,
        "quality": "ACCEPTABLE",
        "deliveryDefect": True,
        "reviewA": "ACCEPTABLE",
        "reviewB": "DEFECTIVE",
    },
    {
        "media": "too_short",
        "disposition": "AUTO_REJECT",
        "watched": False,
        "baselineSeconds": 0.5,
        "assistedSeconds": 0.1,
        "delivered": False,
        "quality": "EXCELLENT",
        "deliveryDefect": None,
        "reviewA": "EXCELLENT",
        "reviewB": "ACCEPTABLE",
    },
    {
        "media": "black",
        "disposition": "AUTO_REJECT",
        "watched": False,
        "baselineSeconds": 2.0,
        "assistedSeconds": 0.1,
        "delivered": False,
        "quality": "DEFECTIVE",
        "deliveryDefect": None,
        "reviewA": "DEFECTIVE",
        "reviewB": "DEFECTIVE",
    },
    {
        "media": "frozen",
        "disposition": "REVIEW_REQUIRED",
        "watched": True,
        "baselineSeconds": 2.0,
        "assistedSeconds": 2.0,
        "delivered": False,
        "quality": "DEFECTIVE",
        "deliveryDefect": None,
        "reviewA": "DEFECTIVE",
        "reviewB": "DEFECTIVE",
    },
    {
        "media": "silent",
        "disposition": "AUTO_REJECT",
        "watched": False,
        "baselineSeconds": 2.0,
        "assistedSeconds": 0.1,
        "delivered": False,
        "quality": "ACCEPTABLE",
        "deliveryDefect": None,
        "reviewA": "ACCEPTABLE",
        "reviewB": "ACCEPTABLE",
    },
    {
        "media": "low_resolution",
        "disposition": "AUTO_REJECT",
        "watched": False,
        "baselineSeconds": 2.0,
        "assistedSeconds": 0.1,
        "delivered": False,
        "quality": "DEFECTIVE",
        "deliveryDefect": None,
        "reviewA": "DEFECTIVE",
        "reviewB": "DEFECTIVE",
    },
    {
        "media": "invalid_container",
        "disposition": "ANALYSIS_ERROR",
        "watched": True,
        "baselineSeconds": 2.0,
        "assistedSeconds": 0.5,
        "delivered": False,
        "quality": "UNREVIEWABLE",
        "deliveryDefect": None,
        "reviewA": "UNREVIEWABLE",
        "reviewB": "UNREVIEWABLE",
    },
    {
        "media": "truncated_container",
        "disposition": "ANALYSIS_ERROR",
        "watched": True,
        "baselineSeconds": 2.0,
        "assistedSeconds": 0.5,
        "delivered": False,
        "quality": "UNREVIEWABLE",
        "deliveryDefect": None,
        "reviewA": "UNREVIEWABLE",
        "reviewB": "",
    },
)


class MediaRunner:
    def __init__(self, kind: str, output_root: Path, image: str | None = None):
        self.kind = kind
        self.output_root = output_root
        self.image = image
        self.identity = self._identity()

    def _identity(self) -> dict[str, str]:
        if self.kind == "host":
            result = subprocess.run(
                ["ffmpeg", "-version"], capture_output=True, text=True, check=True
            )
            return {"runner": "host", "version": result.stdout.splitlines()[0]}
        assert self.image is not None
        result = subprocess.run(
            ["docker", "image", "inspect", self.image, "--format", "{{.Id}}"],
            capture_output=True,
            text=True,
            check=True,
        )
        return {"runner": "docker", "image": self.image, "imageId": result.stdout.strip()}

    def _command(self, executable: str, args: list[str]) -> list[str]:
        if self.kind == "host":
            translated = [
                str(self.output_root / item.removeprefix("/output/"))
                if item.startswith("/output/")
                else item
                for item in args
            ]
            return [executable, *translated]
        assert self.image is not None
        return [
            "docker",
            "run",
            "--rm",
            "--pull=never",
            "--network=none",
            "--cap-drop=ALL",
            "--security-opt=no-new-privileges",
            "-v",
            f"{self.output_root.resolve()}:/output",
            "--entrypoint",
            executable,
            self.image,
            *args,
        ]

    def run(self, executable: str, args: list[str], *, expect_success: bool = True) -> subprocess.CompletedProcess[str]:
        result = subprocess.run(
            self._command(executable, args), capture_output=True, text=True, check=False
        )
        if expect_success and result.returncode != 0:
            message = result.stderr.strip().splitlines()[-1] if result.stderr.strip() else "no stderr"
            raise PilotError(f"{executable} failed: {message[:300]}")
        return result

    def generate_video(self, recipe: MediaRecipe) -> None:
        if recipe.video_source is None or recipe.duration is None:
            raise PilotError(f"recipe {recipe.key} has no FFmpeg source")
        args = [
            "-hide_banner",
            "-loglevel",
            "error",
            "-nostdin",
            "-y",
            "-f",
            "lavfi",
            "-i",
            recipe.video_source,
        ]
        if recipe.audio_hz is not None:
            args.extend(
                [
                    "-f",
                    "lavfi",
                    "-i",
                    f"sine=frequency={recipe.audio_hz}:sample_rate=44100:duration={recipe.duration}",
                ]
            )
        args.extend(
            [
                "-map_metadata",
                "-1",
                "-fflags",
                "+bitexact",
                "-flags:v",
                "+bitexact",
                "-threads",
                "1",
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-pix_fmt",
                "yuv420p",
            ]
        )
        if recipe.audio_hz is not None:
            args.extend(["-c:a", "aac", "-flags:a", "+bitexact", "-shortest"])
        args.extend(["-movflags", "+faststart", f"/output/media/{recipe.file_name}"])
        self.run("ffmpeg", args)

    def extract_keyframe(self, file_name: str, output_name: str) -> None:
        self.run(
            "ffmpeg",
            [
                "-hide_banner",
                "-loglevel",
                "error",
                "-nostdin",
                "-y",
                "-i",
                f"/output/media/{file_name}",
                "-frames:v",
                "1",
                f"/output/images/{output_name}",
            ],
        )

    def probe(self, file_name: str) -> tuple[bool, dict[str, Any] | None]:
        result = self.run(
            "ffprobe",
            [
                "-v",
                "error",
                "-show_entries",
                "format=duration:stream=index,codec_type,width,height,avg_frame_rate",
                "-of",
                "json",
                f"/output/media/{file_name}",
            ],
            expect_success=False,
        )
        if result.returncode != 0:
            return False, None
        try:
            return True, json.loads(result.stdout)
        except json.JSONDecodeError as exc:
            raise PilotError(f"ffprobe emitted invalid JSON for {file_name}") from exc


def resolve_media_runner(output_root: Path, image: str) -> MediaRunner:
    if shutil.which("ffmpeg") and shutil.which("ffprobe"):
        return MediaRunner("host", output_root)
    if shutil.which("docker"):
        inspect = subprocess.run(
            ["docker", "image", "inspect", image], capture_output=True, check=False
        )
        if inspect.returncode == 0:
            return MediaRunner("docker", output_root, image=image)
    raise PilotError(
        "no local ffmpeg/ffprobe pair and the pinned local Docker FFmpeg image is unavailable; "
        "the generator will not pull from the network or create fake video placeholders"
    )


def _file_ref(path: Path, dataset_root: Path) -> dict[str, Any]:
    if path.is_symlink() or not path.is_file():
        raise PilotError(f"dataset file is missing or a symlink: {path}")
    try:
        relative = path.resolve(strict=True).relative_to(dataset_root.resolve(strict=True))
    except ValueError as exc:
        raise PilotError(f"dataset file escapes output root: {path}") from exc
    return {
        "path": relative.as_posix(),
        "sizeBytes": path.stat().st_size,
        "sha256": sha256_file(path),
    }


def _bool_csv(value: bool | None) -> str:
    if value is None:
        return ""
    return "true" if value else "false"


def generate_dataset(output_root: Path, *, image: str = DEFAULT_FFMPEG_IMAGE) -> dict[str, Any]:
    require_repo_root()
    output_root = reset_output_directory(output_root, GENERATED_ROOT, "synthetic output root")
    for child in ("input", "media", "images", "annotations", "stress"):
        (output_root / child).mkdir()

    scenario = read_json(SCENARIO_ROOT / "scenario.json")
    if scenario.get("classification") != CLASSIFICATION:
        raise PilotError("scenario classification is not SYNTHETIC_REHEARSAL")
    if scenario.get("providerMode") != "DISABLED" or scenario.get("realCustomerMedia") is not False:
        raise PilotError("synthetic scenario violates Provider/customer-data policy")
    shutil.copyfile(SCENARIO_ROOT / "brief.md", output_root / "input" / "brief.md")
    shutil.copyfile(SCENARIO_ROOT / "profile.json", output_root / "input" / "profile.json")
    shutil.copyfile(SCENARIO_ROOT / "scenario.json", output_root / "input" / "scenario.json")

    runner = resolve_media_runner(output_root, image)
    paths_by_key: dict[str, Path] = {}
    probes: dict[str, dict[str, Any]] = {}
    for recipe in RECIPES:
        path = output_root / "media" / recipe.file_name
        if recipe.duplicate_of:
            shutil.copyfile(paths_by_key[recipe.duplicate_of], path)
        elif recipe.truncate_from:
            source = paths_by_key[recipe.truncate_from].read_bytes()
            path.write_bytes(source[: min(512, len(source))])
        elif recipe.key == "invalid_container":
            payload = bytearray(2048)
            payload[4:12] = b"ftypisom"
            path.write_bytes(payload)
        else:
            runner.generate_video(recipe)
        paths_by_key[recipe.key] = path

    for recipe in RECIPES:
        success, probe = runner.probe(recipe.file_name)
        should_parse = recipe.key not in {"invalid_container", "truncated_container"}
        if success != should_parse:
            raise PilotError(
                f"media parse expectation failed for {recipe.file_name}: "
                f"expected {should_parse}, got {success}"
            )
        probes[recipe.key] = {"parseSucceeded": success, "probe": probe}

    for key in ("clean_a", "clean_b", "black", "frozen"):
        recipe = next(item for item in RECIPES if item.key == key)
        runner.extract_keyframe(recipe.file_name, f"{key}.png")

    if sha256_file(paths_by_key["clean_a"]) != sha256_file(paths_by_key["clean_a_copy"]):
        raise PilotError("exact duplicate fixture does not have identical bytes")

    rng = random.Random(int(scenario["seed"]))
    shuffled_blind_ids = [f"BR-{index:03d}" for index in range(1, len(CORE_CASES) + 1)]
    rng.shuffle(shuffled_blind_ids)
    core_candidates: list[dict[str, Any]] = []
    annotation_rows: list[dict[str, str]] = []
    reviewer_a_rows: list[dict[str, str]] = []
    reviewer_b_rows: list[dict[str, str]] = []
    recipe_by_key = {recipe.key: recipe for recipe in RECIPES}

    for index, (case, blind_id) in enumerate(zip(CORE_CASES, shuffled_blind_ids), start=1):
        candidate_id = f"SYN-C-{index:03d}"
        recipe = recipe_by_key[case["media"]]
        duplicate_of = "SYN-C-001" if recipe.duplicate_of else None
        core_candidates.append(
            {
                "candidateId": candidate_id,
                "media": _file_ref(paths_by_key[recipe.key], output_root),
                "materialization": "MATERIALIZED",
                "containsCustomerData": False,
                "containsPersonalData": False,
                "duplicateOf": duplicate_of,
                "expectedAnomalies": list(recipe.anomalies),
            }
        )
        annotation_rows.append(
            {
                "candidate_id": candidate_id,
                "batch_id": str(scenario["metricBatchId"]),
                "blind_review_id": blind_id,
                "machine_disposition": case["disposition"],
                "operational_full_watch": _bool_csv(case["watched"]),
                "baseline_full_watch_seconds": str(case["baselineSeconds"]),
                "assisted_review_seconds": str(case["assistedSeconds"]),
                "selected_for_delivery": _bool_csv(case["delivered"]),
                "audit_included": "true",
                "adjudicated_quality": case["quality"],
                "delivery_obvious_defect": _bool_csv(case["deliveryDefect"]),
                "review_notes": "synthetic label used to exercise metric edge cases",
            }
        )
        reviewer_a_rows.append(
            {
                "blind_review_id": blind_id,
                "quality_label": case["reviewA"],
                "obvious_defect": "" if case["reviewA"] == "UNREVIEWABLE" else _bool_csv(case["reviewA"] == "DEFECTIVE"),
                "review_notes": "synthetic reviewer A",
            }
        )
        reviewer_b_rows.append(
            {
                "blind_review_id": blind_id,
                "quality_label": case["reviewB"],
                "obvious_defect": "" if case["reviewB"] in {"", "UNREVIEWABLE"} else _bool_csv(case["reviewB"] == "DEFECTIVE"),
                "review_notes": "synthetic reviewer B" if case["reviewB"] else "synthetic missing label",
            }
        )

    annotation_fields = [
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
        "review_notes",
    ]
    review_fields = ["blind_review_id", "quality_label", "obvious_defect", "review_notes"]
    write_csv(output_root / "annotations" / "adjudicated.csv", annotation_fields, annotation_rows)
    write_csv(output_root / "annotations" / "reviewer-a.csv", review_fields, reviewer_a_rows)
    write_csv(output_root / "annotations" / "reviewer-b.csv", review_fields, reviewer_b_rows)

    runtime_data = {
        "schemaVersion": "frameflow.pilot-batch-runtime.v1",
        "classification": CLASSIFICATION,
        "measurementKind": "SYNTHETIC_SCENARIO_VALUE_NOT_WALL_CLOCK",
        "batches": [
            {
                "batchId": scenario["metricBatchId"],
                "uploadCompletedAt": "2026-08-24T00:01:00Z",
                "rankingReadyAt": "2026-08-24T00:01:42Z",
            },
            {
                "batchId": scenario["stressBatchId"],
                "uploadCompletedAt": None,
                "rankingReadyAt": None,
                "reason": "MANIFEST_VALIDATION_ONLY_NOT_EXECUTED",
            },
        ],
    }
    write_json(output_root / "annotations" / "batch-runtimes.json", runtime_data)

    stress_count = int(scenario["stressCandidateCount"])
    if stress_count != 300:
        raise PilotError("stress scenario must exercise the documented 300-candidate capacity")
    reusable = [recipe for recipe in RECIPES if recipe.key not in {"invalid_container", "truncated_container"}]
    stress_candidates: list[dict[str, Any]] = []
    stress_rows: list[dict[str, str]] = []
    for index in range(1, stress_count + 1):
        recipe = reusable[(index - 1) % len(reusable)]
        candidate_id = f"SYN-STRESS-C-{index:03d}"
        stress_candidates.append(
            {
                "candidateId": candidate_id,
                "media": _file_ref(paths_by_key[recipe.key], output_root),
                "materialization": "REFERENCE_REUSE",
                "containsCustomerData": False,
                "containsPersonalData": False,
                "duplicateOf": None,
                "expectedAnomalies": list(recipe.anomalies),
            }
        )
        stress_rows.append(
            {
                "candidate_id": candidate_id,
                "source_media": recipe.file_name,
                "sha256": sha256_file(paths_by_key[recipe.key]),
                "materialization": "REFERENCE_REUSE",
            }
        )
    write_csv(
        output_root / "stress" / "candidates-300.csv",
        ["candidate_id", "source_media", "sha256", "materialization"],
        stress_rows,
    )

    manifest = {
        "schemaVersion": "frameflow.pilot-manifest.v1",
        "classification": CLASSIFICATION,
        "datasetId": scenario["datasetId"],
        "createdAt": scenario["fixedDatasetTimestamp"],
        "seed": scenario["seed"],
        "dataPolicy": {
            "containsRealCustomerMedia": False,
            "containsPersonalData": False,
            "rightsBasis": "SYNTHETIC_GENERATED",
            "retentionClass": "REGENERABLE",
            "approvalReference": None,
            "deletionDueAt": None,
        },
        "providerExecution": {
            "mode": "DISABLED",
            "realCallsAllowed": False,
            "callEvidenceReferences": [],
        },
        "brief": _file_ref(output_root / "input" / "brief.md", output_root),
        "profile": _file_ref(output_root / "input" / "profile.json", output_root),
        "batches": [
            {
                "batchId": scenario["metricBatchId"],
                "purpose": "METRIC_REHEARSAL",
                "metricScope": True,
                "capacity": len(core_candidates),
                "candidates": core_candidates,
            },
            {
                "batchId": scenario["stressBatchId"],
                "purpose": "MANIFEST_STRESS",
                "metricScope": False,
                "capacity": stress_count,
                "candidates": stress_candidates,
            },
        ],
    }
    write_json(output_root / "manifest.json", manifest)
    write_json(output_root / "media-probes.json", probes)

    anomaly_counts = Counter(
        anomaly for recipe in RECIPES for anomaly in recipe.anomalies
    )
    summary = {
        "schemaVersion": "frameflow.synthetic-pilot-summary.v1",
        "classification": CLASSIFICATION,
        "datasetId": scenario["datasetId"],
        "seed": scenario["seed"],
        "materializedMediaCount": len(RECIPES),
        "generatedImageCount": 4,
        "metricCandidateCount": len(core_candidates),
        "stressCandidateCount": stress_count,
        "stressExecutionMode": "MANIFEST_VALIDATION_ONLY",
        "anomalyCounts": dict(sorted(anomaly_counts.items())),
        "providerCalls": 0,
        "realCustomerMediaCount": 0,
        "mediaTool": runner.identity,
    }
    write_json(output_root / "dataset-summary.json", summary)
    return {"outputRoot": output_root, "manifest": manifest, "summary": summary}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output-root",
        type=Path,
        default=GENERATED_ROOT / "synthetic-rehearsal",
        help="must remain below experiments/pilot/generated",
    )
    parser.add_argument("--ffmpeg-image", default=DEFAULT_FFMPEG_IMAGE)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        result = generate_dataset(args.output_root, image=args.ffmpeg_image)
    except (PilotError, OSError, subprocess.SubprocessError) as exc:
        raise SystemExit(f"synthetic dataset generation failed: {exc}") from exc
    print(result["outputRoot"])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
