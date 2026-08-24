"""spec 规则判定单测。"""

import pytest

from frameflow_ai.detectors import rules
from frameflow_ai.detectors.probe import DetectorError, ProbeResult


def _probe(**kw) -> ProbeResult:
    defaults = dict(duration_ms=8000, width=1080, height=1920, fps=30.0, has_audio=True)
    defaults.update(kw)
    return ProbeResult(**defaults)


def test_duration_violation_is_blocker():
    spec = {"dimensions": {"duration": {"min": 5, "max": 20}}}
    findings = rules.evaluate(spec, _probe(duration_ms=3000))
    assert len(findings) == 1
    assert findings[0].dimension == "duration"
    assert not findings[0].passed
    assert findings[0].severity == "BLOCKER"
    assert findings[0].evidence["durationMs"] == 3000


def test_duration_pass_carries_evidence():
    spec = {"dimensions": {"duration": {"min": 5, "max": 20}}}
    findings = rules.evaluate(spec, _probe(duration_ms=9000))
    assert findings[0].passed and findings[0].severity == "BLOCKER"
    assert findings[0].evidence == {"durationMs": 9000, "min": 5, "max": 20}


def test_unconfigured_dimensions_skipped():
    findings = rules.evaluate({}, _probe())
    assert findings == []


def test_configured_duration_without_measurement_is_detector_error():
    spec = {"dimensions": {"duration": {"min": 5}}}
    with pytest.raises(DetectorError, match="duration"):
        rules.evaluate(spec, _probe(duration_ms=None))


def test_configured_resolution_without_measurement_is_detector_error():
    spec = {"dimensions": {"resolution": {"minWidth": 720, "minHeight": 1280}}}
    with pytest.raises(DetectorError, match="resolution"):
        rules.evaluate(spec, _probe(width=1080, height=None))


def test_configured_fps_without_measurement_is_detector_error():
    spec = {"dimensions": {"fps": {"min": 24}}}
    with pytest.raises(DetectorError, match="fps"):
        rules.evaluate(spec, _probe(fps=None))


def test_resolution_and_fps():
    spec = {"dimensions": {
        "resolution": {"minWidth": 720, "minHeight": 1280},
        "fps": {"min": 24}}}
    findings = rules.evaluate(spec, _probe(width=640, height=480, fps=30.0))
    by_dim = {f.dimension: f for f in findings}
    assert not by_dim["resolution"].passed
    assert by_dim["fps"].passed


def test_severity_override():
    spec = {"dimensions": {"fps": {"min": 30, "severity": "WARNING"}}}
    findings = rules.evaluate(spec, _probe(fps=24.0))
    assert not findings[0].passed
    assert findings[0].severity == "WARNING"   # 不触发 AUTO_REJECT
