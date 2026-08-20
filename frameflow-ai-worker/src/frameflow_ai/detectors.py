"""Deterministic media QA detectors that produce structured findings.

Every detector returns zero or more :class:'Finding' records with machine-readable
facts, a deterministic verdict, and optional time ranges. Detector failures are
raised as DetectorError and must never be converted into a quality reject.
"""
from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass, field
from pathlib import Path

from .ffmpeg import resolve_toolchain
from .media import MediaFacts, aspect_ratio, probe


@dataclass(frozen=True)
class Finding:
    rule_id: str
    detector_id: str
    detector_version: str
    dimension: str
    verdict: str            # SATISFIED | VIOLATED | UNKNOWN
    severity: str           # BLOCKER | MAJOR | MINOR | INFO
    confidence: float       # 0..1 (deterministic rules use 1.0)
    start_ms: int | None
    end_ms: int | None
    summary: str
    evidence: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "ruleId": self.rule_id,
            "detectorId": self.detector_id,
            "detectorVersion": self.detector_version,
            "dimension": self.dimension,
            "verdict": self.verdict,
            "severity": self.severity,
            "confidence": self.confidence,
            "startMs": self.start_ms,
            "endMs": self.end_ms,
            "summary": self.summary,
            "evidence": self.evidence,
        }


class DetectorError(RuntimeError):
    pass


DETECTOR_VERSION = "1.0.0"
FILTER_TIMEOUT_S = 120


def _run_detector(tc, video_path: Path, filter_chain: str, timeout: float = FILTER_TIMEOUT_S) -> str:
    """Run an ffmpeg video detector chain and return its metadata rows (stdout)."""
    cmd = [tc.ffmpeg, "-hide_banner", "-loglevel", "error",
           "-i", str(video_path),
           "-vf", f"{filter_chain},metadata=print:file=-",
           "-an", "-f", "null", "-"]
    return _run_ffmpeg(cmd, "video detector", timeout)


def _run_audio_detector(tc, video_path: Path, filter_chain: str, timeout: float = FILTER_TIMEOUT_S) -> str:
    """Audio-pass detector (silencedetect); disables video so -af applies."""
    cmd = [tc.ffmpeg, "-hide_banner", "-loglevel", "error",
           "-i", str(video_path),
           "-af", f"{filter_chain},ametadata=print:file=-",
           "-vn", "-f", "null", "-"]
    return _run_ffmpeg(cmd, "audio detector", timeout)


def _run_ffmpeg(cmd: list[str], label: str, timeout: float) -> str:
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired as exc:
        raise DetectorError(f"{label} timed out") from exc
    if proc.returncode != 0:
        raise DetectorError(f"{label} failed rc={proc.returncode}: {proc.stderr.strip()[:300]}")
    return proc.stdout + "\n" + proc.stderr


def _parse_metadata(stdout: str) -> dict[str, list[dict]]:
    """Parse 'lavfi.<key>=<value>' metadata rows into {tag: [values]}."""
    out: dict[str, list[dict]] = {}
    for line in stdout.splitlines():
        m = re.match(r"lavfi\.([A-Za-z0-9_.-]+)=(.*)", line.strip())
        if m:
            key = m.group(1)
            value = m.group(2)
            try:
                number = float(value)
            except ValueError:
                continue
            out.setdefault(key, []).append(number)
            tail = key.rsplit(".", 1)[-1]
            if tail and tail != key:
                out.setdefault(tail, []).append(number)
    return out


def _satisfied_finding(rule_id: str, detector: str, dimension: str, summary: str) -> Finding:
    return Finding(rule_id, detector, DETECTOR_VERSION, dimension, "SATISFIED",
                   "INFO", 1.0, None, None, summary, {"intervalCount": 0})


def detect_black(video_path: Path, facts: MediaFacts, tc, min_duration_s: float = 0.5) -> list[Finding]:
    try:
        out = _run_detector(tc, video_path, f"blackdetect=d={min_duration_s}:pix_th=0.10")
    except DetectorError:
        return [Finding("FF-DET-BLACK", "blackdetect", DETECTOR_VERSION, "technical_quality",
                        "UNKNOWN", "INFO", 0.0, None, None, "blackdetect unavailable", {"error": "detector_failed"})]
    tags = _parse_metadata(out)
    starts = tags.get("black_start") or []
    ends = tags.get("black_end") or []
    intervals = _pair_metadata(starts, ends)
    if not intervals:
        return [_satisfied_finding("FF-DET-BLACK", "blackdetect", "technical_quality", "no long black interval")]
    return [Finding("FF-DET-BLACK", "blackdetect", DETECTOR_VERSION, "technical_quality",
                    "VIOLATED", "BLOCKER", 1.0, int(intervals[0][0] * 1000), int(intervals[0][1] * 1000),
                    f"long black interval {intervals[0][1]-intervals[0][0]:.2f}s", {"intervals": intervals})]


def _pair_metadata(starts: list[dict], ends: list[dict]) -> list[tuple[float, float]]:
    pairs = []
    for s, e in zip(starts, ends):
        pairs.append((s, e))
    # leftover open start -> close with +0.5s
    if len(starts) > len(ends):
        pairs.append((starts[-1], starts[-1] + 0.5))
    return pairs


def detect_freeze(video_path: Path, facts: MediaFacts, tc, min_duration_s: float = 0.5) -> list[Finding]:
    try:
        out = _run_detector(tc, video_path, f"freezedetect=d={min_duration_s}:n=0.0001")
    except DetectorError:
        return [Finding("FF-DET-FREEZE", "freezedetect", DETECTOR_VERSION, "temporal_consistency",
                        "UNKNOWN", "INFO", 0.0, None, None, "freezedetect unavailable", {"error": "detector_failed"})]
    tags = _parse_metadata(out)
    starts = tags.get("freeze_start") or []
    durations = tags.get("freeze_duration") or []
    events = []
    for i in range(max(len(starts), len(durations))):
        s = starts[i] if i < len(starts) else 0.0
        d = durations[i] if i < len(durations) else 0.5
        events.append((s, d))
    if not events:
        return [_satisfied_finding("FF-DET-FREEZE", "freezedetect", "temporal_consistency", "no long freeze")]
    return [Finding("FF-DET-FREEZE", "freezedetect", DETECTOR_VERSION, "temporal_consistency",
                    "VIOLATED", "BLOCKER", 1.0, int(events[0][0] * 1000),
                    int((events[0][0] + events[0][1]) * 1000),
                    f"frozen frame {events[0][1]:.2f}s", {"events": events})]


def detect_silence(video_path: Path, facts: MediaFacts, tc) -> list[Finding]:
    if not facts.has_audio:
        return []
    try:
        out = _run_audio_detector(tc, video_path, "silencedetect=noise=-35dB:d=1.5")
    except DetectorError:
        return [Finding("FF-DET-SILENCE", "silencedetect", DETECTOR_VERSION, "audio_subtitle_quality",
                        "UNKNOWN", "INFO", 0.0, None, None, "silencedetect unavailable", {"error": "detector_failed"})]
    tags = _parse_metadata(out)
    durations = tags.get("silence_duration") or []
    total_silence = sum(durations)
    duration = facts.duration_seconds or (total_silence + 1.0)
    if duration > 0 and total_silence / duration >= 0.5:
        return [Finding("FF-DET-SILENCE", "silencedetect", DETECTOR_VERSION, "audio_subtitle_quality",
                        "VIOLATED", "BLOCKER", 1.0, None, None,
                        f"excessive silence {total_silence:.1f}s / {duration:.1f}s",
                        {"totalSilenceSeconds": round(total_silence, 3), "durationSeconds": duration})]
    return [Finding("FF-DET-SILENCE", "silencedetect", DETECTOR_VERSION, "audio_subtitle_quality",
                    "SATISFIED", "INFO", 1.0, None, None, "silence within norms",
                    {"totalSilenceSeconds": round(total_silence, 3)})]


def detect_spec(video_path: Path, facts: MediaFacts, profile_input: dict) -> list[Finding]:
    """Spec rules: duration range, aspect ratio, resolution, audio presence."""
    out: list[Finding] = []
    if profile_input.get("durationSeconds"):
        dmin = profile_input["durationSeconds"].get("minimum")
        dmax = profile_input["durationSeconds"].get("maximum")
        dur = facts.duration_seconds
        if dur is not None and (dur < dmin or dur > dmax):
            out.append(Finding("FF-RULE-DURATION", "ffprobe", DETECTOR_VERSION, "technical_quality",
                               "VIOLATED", "BLOCKER", 1.0, None, None,
                               "duration " + format(dur, ".2f") + "s outside [" + str(dmin) + "," + str(dmax) + "]",
                               {"durationSeconds": dur, "minimum": dmin, "maximum": dmax}))
        else:
            out.append(Finding("FF-RULE-DURATION", "ffprobe", DETECTOR_VERSION, "technical_quality",
                               "SATISFIED", "INFO", 1.0, None, None, "duration within range", {"durationSeconds": dur}))
    ar = profile_input.get("aspectRatio")
    if ar:
        expected = ar.get("expected")
        tolerance = ar.get("tolerance", 0.02)
        actual = aspect_ratio(facts.width, facts.height)
        if actual and expected:
            exp_w, exp_h = (int(x) for x in expected.split(":"))
            actual_value = facts.width / facts.height
            expected_value = exp_w / exp_h
            if abs(actual_value - expected_value) > tolerance:
                out.append(Finding("FF-RULE-ASPECT", "ffprobe", DETECTOR_VERSION, "technical_quality",
                                   "VIOLATED", "BLOCKER", 1.0, None, None,
                                   "aspect " + actual + " != expected " + expected,
                                   {"width": facts.width, "height": facts.height, "expected": expected, "tolerance": tolerance}))
            else:
                out.append(Finding("FF-RULE-ASPECT", "ffprobe", DETECTOR_VERSION, "technical_quality",
                                   "SATISFIED", "INFO", 1.0, None, None, "aspect " + actual + " ok", {"aspect": actual}))
    min_res = profile_input.get("minResolution")
    if min_res and facts.width and facts.height and (facts.width * facts.height) < min_res["width"] * min_res["height"]:
        out.append(Finding("FF-RULE-RESOLUTION", "ffprobe", DETECTOR_VERSION, "technical_quality",
                           "VIOLATED", "BLOCKER", 1.0, None, None,
                           "resolution " + str(facts.width) + "x" + str(facts.height) + " below min",
                           {"width": facts.width, "height": facts.height, "min": min_res}))
    if profile_input.get("audioRequired"):
        if not facts.has_audio:
            out.append(Finding("FF-RULE-AUDIO", "ffprobe", DETECTOR_VERSION, "audio_subtitle_quality",
                               "VIOLATED", "BLOCKER", 1.0, None, None,
                               "audio track missing but required", {"hasAudio": facts.has_audio}))
        else:
            out.append(Finding("FF-RULE-AUDIO", "ffprobe", DETECTOR_VERSION, "audio_subtitle_quality",
                               "SATISFIED", "INFO", 1.0, None, None, "audio present", {"hasAudio": True}))
    return out
