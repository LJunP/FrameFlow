"""pipeline 语义测试：ANALYSIS_ERROR 红线 + 组装回写载荷。"""

import numpy as np

from frameflow_ai import pipeline
from frameflow_ai.detectors import frames as frames_mod
from frameflow_ai.detectors.probe import DetectorError, ProbeResult


def test_detector_error_is_analysis_error_not_blocker(monkeypatch):
    """缺 ffprobe → ok=False + errorSummary，绝无 BLOCKER finding。"""

    def boom(_path):
        raise DetectorError("ffprobe 不在 PATH 中")

    monkeypatch.setattr(pipeline, "probe", boom)
    outcome = pipeline.analyze({"runId": 7, "objectKey": "k"}, lambda k, l: None, "test")
    assert outcome.ok is False
    assert "ffprobe" in outcome.error_summary
    payload = outcome.to_payload()
    assert "findings" not in payload and payload["errorSummary"]


def test_happy_path_payload_shape(monkeypatch):
    monkeypatch.setattr(pipeline, "probe",
                        lambda _p: ProbeResult(8000, 1080, 1920, 30.0, True))
    monkeypatch.setattr(pipeline, "_frame_checks",
                        lambda _p, _r: ([], {}))
    task = {"runId": 9, "objectKey": "k", "profileSpec": '{"dimensions":{"duration":{"min":5,"max":20}}}'}
    outcome = pipeline.analyze(task, lambda k, l: None, "test")
    payload = outcome.to_payload()
    assert payload["ok"] is True
    assert payload["durationMs"] == 8000
    assert payload["findings"][0]["dimension"] == "duration"
    assert payload["findings"][0]["passed"] is True


def test_unexpected_exception_also_analysis_error(monkeypatch):
    def boom(_k, _l):
        raise RuntimeError("disk full")

    monkeypatch.setattr(pipeline, "probe", lambda _p: ProbeResult(1, 1, 1, 1.0, False))
    outcome = pipeline.analyze({"runId": 1, "objectKey": "k"}, boom, "test")
    assert outcome.ok is False
    assert "unexpected" in outcome.error_summary
