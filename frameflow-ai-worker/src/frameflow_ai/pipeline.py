"""一次分析的编排：下载 → 探测 → 规则判定 → 帧检测 → 组装回写结果。

★ ANALYSIS_ERROR 语义的唯一裁决点：任何 DetectorError 都转成
ok=False 的结果，让候选进 ANALYSIS_ERROR（系统问题），
绝不产生 BLOCKER finding（那才是"视频不合格"）。
"""

from __future__ import annotations

import json
import os
import tempfile
from dataclasses import dataclass, field

from .detectors import DetectorError, frames, probe, rules
from .detectors.frames import FrameSegment


@dataclass
class AnalysisOutcome:
    run_id: int
    ok: bool
    worker_version: str
    error_summary: str | None = None
    findings: list[dict] = field(default_factory=list)
    probe_info: dict = field(default_factory=dict)

    def to_payload(self) -> dict:
        payload = {
            "runId": self.run_id,
            "workerVersion": self.worker_version,
            "ok": self.ok,
        }
        if self.ok:
            payload["findings"] = self.findings
            payload.update(self.probe_info)
        else:
            payload["errorSummary"] = self.error_summary
        return payload


def analyze(task: dict, download, worker_version: str) -> AnalysisOutcome:
    """执行分析。download(object_key) -> 本地文件路径（由调用方注入）。"""
    run_id = int(task["runId"])
    try:
        with tempfile.TemporaryDirectory(prefix="frameflow-") as tmp:
            local = os.path.join(tmp, "candidate.bin")
            download(task["objectKey"], local)
            return _run_detectors(run_id, local, task.get("profileSpec") or "{}", worker_version)
    except DetectorError as e:
        # 系统侧失败：ANALYSIS_ERROR（红线：不得伪装成视频不合格）
        return AnalysisOutcome(run_id=run_id, ok=False, worker_version=worker_version,
                               error_summary=f"[{type(e).__name__}] {e}")
    except Exception as e:  # noqa: BLE001 兜底也走 ANALYSIS_ERROR，绝不让消息无限重试
        return AnalysisOutcome(run_id=run_id, ok=False, worker_version=worker_version,
                               error_summary=f"[unexpected:{type(e).__name__}] {e}")


def _run_detectors(run_id: int, local_path: str, spec_json: str,
                   worker_version: str) -> AnalysisOutcome:
    spec = json.loads(spec_json) if spec_json else {}
    probe_result = probe(local_path)

    findings: list[dict] = [
        f.to_payload() for f in rules.evaluate(spec, probe_result)
    ]
    frame_findings, probe_info = _frame_checks(local_path, probe_result)
    findings.extend(frame_findings)

    info = {k: v for k, v in probe_result.to_dict().items() if k != "hasAudio"}
    return AnalysisOutcome(run_id=run_id, ok=True, worker_version=worker_version,
                           findings=findings, probe_info=info)


def _frame_checks(path: str, probe_result) -> tuple[list[dict], dict]:
    """黑帧/冻结检测；不含帧级规则时不抽帧（省 CPU）。"""
    frame_findings: list[dict] = []
    sampled, stamps = frames.sample_frames(path)

    black = frames.find_black_segments(sampled, stamps)
    if black:
        frame_findings.append(_segment_finding("black_frame", black, "检测到连续黑帧"))
    else:
        frame_findings.append(_passed_finding("black_frame", "未检测到黑帧区间"))

    freeze = frames.find_freeze_segments(sampled, stamps)
    if freeze:
        frame_findings.append(_segment_finding("freeze", freeze, "检测到画面冻结区间"))
    else:
        frame_findings.append(_passed_finding("freeze", "未检测到画面冻结"))

    return frame_findings, {}


def _segment_finding(dimension: str, segments: list[FrameSegment], message: str) -> dict:
    return {
        "detector": frames.DETECTOR_ID,
        "detectorVersion": frames.DETECTOR_VERSION,
        "dimension": dimension,
        "passed": False,
        "severity": "BLOCKER",
        "timecodeMs": segments[0].start_ms,
        "evidence": json.dumps({
            "segments": [{"startMs": s.start_ms, "endMs": s.end_ms, "frames": s.frames}
                         for s in segments[:10]],
        }, ensure_ascii=False),
        "message": message,
    }


def _passed_finding(dimension: str, message: str) -> dict:
    return {
        "detector": frames.DETECTOR_ID,
        "detectorVersion": frames.DETECTOR_VERSION,
        "dimension": dimension,
        "passed": True,
        "severity": "INFO",
        "timecodeMs": None,
        "evidence": json.dumps({"checkedFrames": "sampled"}),
        "message": message,
    }
