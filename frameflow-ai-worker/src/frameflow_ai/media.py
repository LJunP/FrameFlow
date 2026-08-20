"""FFprobe-driven media facts and probing."""
from __future__ import annotations

import json
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from .ffmpeg import resolve_toolchain


@dataclass
class MediaFacts:
    container: str | None = None
    duration_seconds: float | None = None
    bit_rate: int | None = None
    width: int | None = None
    height: int | None = None
    rotation: int | None = None
    fps: float | None = None
    has_video: bool = False
    has_audio: bool = False
    audio_codec: str | None = None
    audio_channels: int | None = None
    video_codec: str | None = None
    bit_depth: int | None = None
    raw: dict = field(default_factory=dict)


def probe(path: str | Path, toolchain: MediaToolchain | None = None) -> MediaFacts:
    tc = toolchain or resolve_toolchain()
    proc = subprocess.run(
        [tc.ffprobe, "-v", "error", "-show_format", "-show_streams", "-of", "json", str(path)],
        capture_output=True,
        text=True,
    )
    if proc.returncode != 0:
        raise ProbeError(f"ffprobe failed on {path}: {proc.stderr.strip()}")
    doc = json.loads(proc.stdout)
    facts = MediaFacts(raw=doc)
    fmt = doc.get("format", {})
    facts.container = fmt.get("format_name")
    try:
        facts.duration_seconds = float(fmt["duration"]) if fmt.get("duration") else None
    except (KeyError, ValueError, TypeError):
        facts.duration_seconds = None
    try:
        facts.bit_rate = int(fmt.get("bit_rate")) if fmt.get("bit_rate") else None
    except (ValueError, TypeError):
        facts.bit_rate = None

    for stream in doc.get("streams", []):
        codec_type = stream.get("codec_type")
        if codec_type == "video" and not facts.has_video:
            facts.has_video = True
            facts.video_codec = stream.get("codec_name")
            try:
                facts.width = int(stream.get("width"))
                facts.height = int(stream.get("height"))
            except (TypeError, ValueError):
                pass
            side = stream.get("side_data_list") or []
            for item in side:
                if item.get("rotation") is not None:
                    try:
                        facts.rotation = int(float(item["rotation"]))
                    except (TypeError, ValueError):
                        pass
            try:
                fps_text = stream.get("avg_frame_rate") or stream.get("r_frame_rate")
                num, _, den = str(fps_text).partition("/")
                if den and float(den) > 0:
                    facts.fps = float(num) / float(den)
            except (ValueError, ZeroDivisionError):
                pass
            try:
                facts.bit_depth = int(stream.get("bits_per_raw_sample") or 0)
            except (ValueError, TypeError):
                facts.bit_depth = None
        elif codec_type == "audio" and not facts.has_audio:
            facts.has_audio = True
            facts.audio_codec = stream.get("codec_name")
            try:
                facts.audio_channels = int(stream.get("channels"))
            except (TypeError, ValueError):
                pass
    return facts


class ProbeError(RuntimeError):
    pass


def aspect_ratio(width: int | None, height: int | None) -> str | None:
    if not width or not height:
        return None
    import math
    g = math.gcd(width, height)
    return f"{width // g}:{height // g}"
