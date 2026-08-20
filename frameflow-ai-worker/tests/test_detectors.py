from pathlib import Path

import pytest

from frameflow_ai.detectors import detect_black, detect_freeze, detect_silence, detect_spec
from frameflow_ai.ffmpeg import resolve_toolchain
from frameflow_ai.media import probe

GENERATED = Path(__file__).resolve().parents[2] / "experiments/fixtures/generated"


@pytest.fixture(scope="module")
def tc():
    return resolve_toolchain()


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_duration_rule_too_short(profile, tc):
    facts = probe(GENERATED / "too_short.mp4")
    findings = detect_spec(GENERATED / "too_short.mp4", facts, {
        "durationSeconds": {"minimum": 5, "maximum": 60},
        "aspectRatio": {"expected": "9:16", "tolerance": 0.02},
        "audioRequired": True,
    })
    dur = next(f for f in findings if f.rule_id == "FF-RULE-DURATION")
    assert dur.verdict == "VIOLATED"
    assert dur.severity == "BLOCKER"
    assert dur.confidence == 1.0


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_aspect_rule_wrong(profile, tc):
    facts = probe(GENERATED / "wrong_aspect.mp4")
    findings = detect_spec(GENERATED / "wrong_aspect.mp4", facts, {
        "durationSeconds": {"minimum": 5, "maximum": 60},
        "aspectRatio": {"expected": "9:16", "tolerance": 0.02},
        "audioRequired": True,
    })
    asp = next(f for f in findings if f.rule_id == "FF-RULE-ASPECT")
    assert asp.verdict == "VIOLATED"


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_audio_missing_rule(profile, tc):
    facts = probe(GENERATED / "silent_vertical.mp4")
    findings = detect_spec(GENERATED / "silent_vertical.mp4", facts, {
        "durationSeconds": {"minimum": 5, "maximum": 60},
        "aspectRatio": {"expected": "9:16", "tolerance": 0.02},
        "audioRequired": True,
    })
    aud = next(f for f in findings if f.rule_id == "FF-RULE-AUDIO")
    assert aud.verdict == "VIOLATED"


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_black_detector(profile, tc):
    facts = probe(GENERATED / "black_video.mp4")
    findings = detect_black(GENERATED / "black_video.mp4", facts, tc)
    assert any(f.verdict == "VIOLATED" for f in findings)


@pytest.mark.skipif(not GENERATED.exists(), reason="fixture dataset not generated")
def test_freeze_detector(profile, tc):
    facts = probe(GENERATED / "freeze_video.mp4")
    findings = detect_freeze(GENERATED / "freeze_video.mp4", facts, tc)
    assert any(f.verdict == "VIOLATED" for f in findings)
