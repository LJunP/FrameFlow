"""Deterministic analysis pipeline executor (S1 local feasibility; reused by worker).

Pipeline stages (BOOK-03 6.x):
  S0 Ingest -> S1 Probe & Validate -> S2 Derive Media (skip in S1) ->
  S3 Deterministic QA -> S4 Specialized Analysis (provider) -> S5 Semantic
  Assertions -> S6 Batch Similarity (cluster, saved separately) ->
  S7 Decision & Ranking features
"""
from __future__ import annotations

import hashlib
import json
import time
import uuid
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from .detectors import detect_black, detect_freeze, detect_silence, detect_spec, probe
from . import detectors
from .ffmpeg import resolve_toolchain
from .media import MediaFacts
from .quality import Decision, apply_profile
from .semantic import FakeSemanticProvider, SemanticProvider, SemanticVerdict


@dataclass
class RunResult:
    status: str
    decision: Decision
    findings: list[dict]
    media_facts: dict
    quality_vector: dict
    usage: list[dict]
    warnings: list[str] = field(default_factory=list)


def _normalize_profile(profile: dict) -> dict:
    """Coerce the authoritative quality-profile schema into the worker's shape."""
    inp = profile.get("inputContract", {})
    return {
        "durationSeconds": inp.get("durationSeconds"),
        "aspectRatio": inp.get("aspectRatio"),
        "audioRequired": inp.get("audioRequired"),
        "minResolution": inp.get("minResolution"),
        "rules": profile.get("rules", []),
        "automationPolicy": profile.get("automationPolicy", {}),
    }


def run_pipeline(video_path: Path, profile: dict,
                 provider: SemanticProvider | None = None,
                 brief_assertions: list[dict] | None = None) -> RunResult:
    tc = resolve_toolchain()
    warnings: list[str] = []

    try:
        facts: MediaFacts = probe(video_path, tc)
    except Exception as exc:
        return RunResult("FAILED", Decision("ANALYSIS_ERROR", False, [str(exc)]),
                         [], {}, {}, [], [str(exc)])

    p = _normalize_profile(profile)
    findings: list[dict] = []
    # S3 Deterministic QA
    for det in (detect_spec(video_path, facts, p),
                detect_black(video_path, facts, tc),
                detect_freeze(video_path, facts, tc),
                detect_silence(video_path, facts, tc)):
        for f in det:
            findings.append(f.to_dict())

    # S5 Semantic assertions via provider
    provider = provider or FakeSemanticProvider()
    for assertion in brief_assertions or []:
        verdict: SemanticVerdict = provider.evaluate(assertion, {
            "mediaAvailable": facts.has_video,
            "forcedOutcome": assertion.get("forcedOutcome"),
            "mediaFacts": _facts_dict(facts),
        })
        findings.append(_semantic_finding(assertion, verdict))

    decision = apply_profile(profile, findings,
                             hashlib.sha256(json.dumps(profile, sort_keys=True).encode()).hexdigest()[:16])

    qv = _quality_vector(findings)
    usage = provider.usage if isinstance(provider, FakeSemanticProvider) else []
    status = "COMPLETED_WITH_FINDINGS" if any(f["verdict"] == "VIOLATED" for f in findings) else "COMPLETED"
    return RunResult(status, decision, findings, _facts_dict(facts), qv, usage, warnings)


def _facts_dict(facts: MediaFacts) -> dict:
    return {
        "container": facts.container,
        "durationSeconds": facts.duration_seconds,
        "width": facts.width,
        "height": facts.height,
        "rotation": facts.rotation,
        "fps": facts.fps,
        "hasVideo": facts.has_video,
        "hasAudio": facts.has_audio,
        "audioCodec": facts.audio_codec,
        "videoCodec": facts.video_codec,
    }


def _semantic_finding(assertion: dict, verdict: SemanticVerdict) -> dict:
    return {
        "findingId": str(uuid.uuid4()),
        "ruleId": assertion.get("ruleId", "FF-ASSERT-" + assertion.get("assertionId", "?")),
        "dimension": assertion.get("dimension", "prompt_alignment"),
        "verdict": {"PASS": "SATISFIED", "VIOLATE": "VIOLATED",
                    "UNKNOWN": "UNKNOWN", "ERROR": "UNKNOWN"}.get(verdict.verdict, "UNKNOWN"),
        "severity": "MAJOR" if verdict.verdict == "VIOLATE" else "INFO",
        "confidence": verdict.confidence,
        "automationAction": "REVIEW",
        "startMs": verdict.start_ms,
        "endMs": verdict.end_ms,
        "summary": verdict.summary,
        "evidence": verdict.evidence,
        "origin": {"kind": "semantic", "provider": verdict.evidence.get("provider"),
                   "assertionId": assertion.get("assertionId")},
    }


def _quality_vector(findings: list[dict]) -> dict:
    dims = ["technical_quality", "visual_stability", "temporal_consistency",
            "prompt_alignment", "product_visibility", "brand_compliance",
            "audio_subtitle_quality", "aesthetic_fitness"]
    vec = {}
    for dim in dims:
        related = [f for f in findings if f.get("dimension") == dim]
        if not related:
            vec[dim] = 0.5
            continue
        passes = [f for f in related if f["verdict"] == "SATISFIED"]
        vec[dim] = round(len(passes) / len(related), 3) if related else 0.5
    return vec
