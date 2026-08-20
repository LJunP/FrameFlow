"""Worker diagnostic CLIs: media probe, fixture generation, single-file analysis."""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .ffmpeg import resolve_toolchain
from .media import probe


def main_probe(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="Probe a media file (machine-readable facts).")
    ap.add_argument("path")
    args = ap.parse_args(argv)
    tc = resolve_toolchain()
    facts = probe(args.path, tc)
    doc = {
        "ok": True,
        "mediaFacts": {
            "container": facts.container,
            "durationSeconds": facts.duration_seconds,
            "bitRate": facts.bit_rate,
            "width": facts.width,
            "height": facts.height,
            "rotation": facts.rotation,
            "fps": facts.fps,
            "hasVideo": facts.has_video,
            "hasAudio": facts.has_audio,
            "audioCodec": facts.audio_codec,
            "audioChannels": facts.audio_channels,
            "videoCodec": facts.video_codec,
        },
    }
    print(json.dumps(doc, ensure_ascii=False, indent=2))
    return 0


def main_analyze(argv: list[str]) -> int:
    ap = argparse.ArgumentParser(description="Analyze one video against a quality profile (JSON).")
    ap.add_argument("video")
    ap.add_argument("profile", help="path to quality profile JSON")
    ap.add_argument("--brief", help="optional brief assertions JSON")
    args = ap.parse_args(argv)
    profile = json.loads(Path(args.profile).read_text())
    brief = json.loads(Path(args.brief).read_text()) if args.brief else None
    from .pipeline import run_pipeline
    result = run_pipeline(Path(args.video), profile, brief_assertions=brief)
    doc = {
        "ok": result.status != "FAILED",
        "status": result.status,
        "decision": {"value": result.decision.value, "automatic": result.decision.automatic,
                     "reasons": result.decision.reasons},
        "findings": result.findings,
        "mediaFacts": result.media_facts,
        "qualityVector": result.quality_vector,
        "usage": result.usage,
        "warnings": result.warnings,
    }
    print(json.dumps(doc, ensure_ascii=False, indent=2))
    return 0 if result.status != "FAILED" else 2


def main() -> int:
    argv = sys.argv[1:]
    if not argv:
        print("usage: frameflow-ai {probe|analyze|fixtures} ...", file=sys.stderr)
        return 2
    cmd, rest = argv[0], argv[1:]
    if cmd == "probe":
        return main_probe(rest)
    if cmd == "analyze":
        return main_analyze(rest)
    if cmd == "fixtures":
        from .fixtures import main_fixtures
        return main_fixtures(rest)
    if cmd == "eval":
        from .eval import main_eval
        return main_eval(rest)
    print(f"unknown command: {cmd}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
