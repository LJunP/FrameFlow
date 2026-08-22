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
    # 桩须带 DETECTOR_ID/DETECTOR_VERSION：帧检测构造"通过项 Finding"
    # 时引用这两个模块常量，缺了会 AttributeError 走 ANALYSIS_ERROR 分支
    monkeypatch.setattr(pipeline, "frames",
                        type("F", (), {
                            "DETECTOR_ID": "stub-frames",
                            "DETECTOR_VERSION": "1",
                            "sample_frames": staticmethod(lambda _p: ([], [])),
                            "find_black_segments": staticmethod(lambda f, t: []),
                            "find_freeze_segments": staticmethod(lambda f, t: []),
                        }))
    task = {"runId": 9, "objectKey": "k",
            "profileSpec": '{"dimensions":{"duration":{"min":5,"max":20}}}',
            "briefContent": "b"}

    def download(_key, local):
        # F7 起管线会计算文件 SHA-256，桩必须真的落盘
        with open(local, "wb") as f:
            f.write(b"stub-bytes")

    outcome = pipeline.analyze(task, download, "test")
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
