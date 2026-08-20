"""Resolve FFmpeg/ffprobe binaries.

Primary source: the static-ffmpeg PyPI package (pinned, official PyPI) which ships
platform binaries in the user cache. If the platform binary cannot be fetched, we
fall back to a system binary if present. Callers must surface a clear error when
the media toolchain is unavailable (per DECISION-POLICY: never a silent hard stop).
"""
from __future__ import annotations

import shutil
from dataclasses import dataclass


@dataclass(frozen=True)
class MediaToolchain:
    ffmpeg: str
    ffprobe: str


def resolve_toolchain() -> MediaToolchain:
    try:
        from static_ffmpeg import run  # type: ignore
        ffmpeg, ffprobe = run.get_or_fetch_platform_executables_else_raise()
        return MediaToolchain(ffmpeg=str(ffmpeg), ffprobe=str(ffprobe))
    except Exception:
        system_ffmpeg = shutil.which("ffmpeg")
        system_ffprobe = shutil.which("ffprobe")
        if system_ffmpeg and system_ffprobe:
            return MediaToolchain(system_ffmpeg, system_ffprobe)
        raise RuntimeError(
            "NO_FFMPEG: no ffmpeg/ffprobe available. Install static-ffmpeg "
            "(pip install static-ffmpeg) or provide system ffmpeg."
        )
