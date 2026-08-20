"""Synthetic, copyright-free fixture dataset generator (FF-FEA-001).

Only lavfi synthetic sources (testsrc2, smptebars, color, sine) are used, so the
resulting media is reproducible and copyright-safe. Overlays/watermarks are NOT
added; the goal is deterministic technical QA fixtures, not aesthetic ground truth.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import time
from pathlib import Path

from .ffmpeg import resolve_toolchain


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def run(cmd: list[str]) -> None:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode:
        raise RuntimeError(f"ffmpeg failed ({proc.returncode}): {proc.stderr[:400]}")


def make(ffmpeg: str, out: Path, name: str, video_filter: str, size: str = "360x640",
         audio: bool = True, duration: int = 6, extra_vf: list[str] | None = None) -> dict:
    if video_filter == "testsrc2":
        source = f"testsrc2=size={size}:rate=24"
    elif video_filter.startswith("color="):
        source = f"{video_filter}:size={size}:rate=24"
    elif video_filter == "smptebars":
        source = f"smptebars=size={size}:rate=24"
    else:
        raise ValueError(f"unsupported synthetic source: {video_filter}")
    cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", "-y", "-f", "lavfi", "-i", source]
    if audio:
        cmd += ["-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000"]
    cmd += ["-t", str(duration), "-c:v", "libx264", "-preset", "ultrafast", "-crf", "28",
            "-pix_fmt", "yuv420p"]
    if audio:
        cmd += ["-c:a", "aac", "-shortest"]
    if extra_vf:
        cmd += ["-vf", ",".join(extra_vf)]
    cmd += ["-movflags", "+faststart", str(out)]
    run(cmd)
    return {"id": name, "file": out.name, "sha256": sha256(out)}


LABELS = {
    "normal_vertical": {"expected": ["DECODE_OK", "ASPECT_9_16", "HAS_AUDIO"]},
    "wrong_aspect": {"expected": ["ASPECT_MISMATCH"]},
    "silent_vertical": {"expected": ["AUDIO_MISSING"]},
    "black_video": {"expected": ["BLACK_INTERVAL"]},
    "freeze_video": {"expected": ["FREEZE_INTERVAL"]},
    "too_short": {"expected": ["DURATION_TOO_SHORT"]},
    "duplicate_source": {"duplicateGroup": "exact-1"},
    "duplicate_copy": {"duplicateGroup": "exact-1"},
    "near_duplicate": {"expected": ["REVIEW_SIMILARITY"]},
    "low_resolution": {"expected": ["RESOLUTION_TOO_LOW"]},
}


def generate(output: str, duration: int = 6) -> dict:
    out = Path(output).resolve()
    out.mkdir(parents=True, exist_ok=True)
    tc = resolve_toolchain()
    ffmpeg = tc.ffmpeg
    cases: list[dict] = []

    c = make(ffmpeg, out / "normal_vertical.mp4", "normal_vertical", "testsrc2")
    cases.append(c)
    c = make(ffmpeg, out / "wrong_aspect.mp4", "wrong_aspect", "testsrc2", size="640x360")
    cases.append(c)
    c = make(ffmpeg, out / "silent_vertical.mp4", "silent_vertical", "testsrc2", audio=False)
    cases.append(c)
    c = make(ffmpeg, out / "black_video.mp4", "black_video", "color=c=black")
    cases.append(c)
    # freeze: same color frame => freezedetect sees a still sequence
    c = make(ffmpeg, out / "freeze_video.mp4", "freeze_video", "color=c=blue")
    cases.append(c)
    c = make(ffmpeg, out / "too_short.mp4", "too_short", "testsrc2", duration=1)
    cases.append(c)
    original = make(ffmpeg, out / "duplicate_source.mp4", "duplicate_source", "testsrc2")
    cases.append(original)
    dup_path = out / "duplicate_copy.mp4"
    shutil.copy2(out / "duplicate_source.mp4", dup_path)
    cases.append({"id": "duplicate_copy", "file": "duplicate_copy.mp4", "sha256": sha256(dup_path)})
    c = make(ffmpeg, out / "near_duplicate.mp4", "near_duplicate", "testsrc2",
             extra_vf=["hflip"])
    cases.append(c)
    c = make(ffmpeg, out / "low_resolution.mp4", "low_resolution", "testsrc2", size="180x320")
    cases.append(c)

    dawn = time.time()
    for case in cases:
        case.update(LABELS.get(case["id"], {}))
    manifest = {
        "schemaVersion": "1.0.0",
        "source": "SYNTHETIC_FFMPEG_LAVFI",
        "generatedAtUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(dawn)),
        "profile": {
            "durationSeconds": {"minimum": duration - 1, "maximum": 60},
            "aspectRatio": {"expected": "9:16", "tolerance": 0.02},
            "audioRequired": True,
            "minResolution": {"width": 320, "height": 568},
        },
        "cases": cases,
    }
    (out / "dataset-manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {"status": "OK", "output": str(out), "cases": len(cases), "binaries": {"ffmpeg": tc.ffmpeg}}


def main_fixtures(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="Generate synthetic fixture dataset.")
    ap.add_argument("--output", default="experiments/fixtures/generated")
    ap.add_argument("--duration", type=int, default=6)
    args = ap.parse_args(argv)
    try:
        doc = generate(args.output, args.duration)
        print(json.dumps(doc, ensure_ascii=False, indent=2))
        return 0
    except Exception as exc:
        print(json.dumps({"status": "ERROR", "error": str(exc)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main_fixtures(sys.argv[1:]))
