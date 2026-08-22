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
            return _run_detectors(run_id, local, task, worker_version)
    except DetectorError as e:
        # 系统侧失败：ANALYSIS_ERROR（红线：不得伪装成视频不合格）
        return AnalysisOutcome(run_id=run_id, ok=False, worker_version=worker_version,
                               error_summary=f"[{type(e).__name__}] {e}")
    except Exception as e:  # noqa: BLE001 兜底也走 ANALYSIS_ERROR，绝不让消息无限重试
        return AnalysisOutcome(run_id=run_id, ok=False, worker_version=worker_version,
                               error_summary=f"[unexpected:{type(e).__name__}] {e}")


def _run_detectors(run_id: int, local_path: str, task: dict,
                   worker_version: str) -> AnalysisOutcome:
    spec = json.loads(task.get("profileSpec") or "{}") or {}
    probe_result = probe(local_path)

    findings: list[dict] = [
        f.to_payload() for f in rules.evaluate(spec, probe_result)
    ]
    # 抽帧一次，帧检测与语义阶段共用（解码是最贵的 CPU 步骤）
    sampled, stamps = frames.sample_frames(local_path)
    findings.extend(_frame_checks(sampled, stamps))

    # F6 语义阶段：spec.semantic.enabled 时运行（Provider 失败 → ERROR 判定
    # 进人工复核，绝不拖垮整个 run——见 semantic.run_semantic ★ 注释）
    from . import semantic
    findings.extend(semantic.run_semantic(
        spec, task.get("briefContent") or "", local_path,
        sampled, stamps, provider=None, candidate_hint=task.get("objectKey", "")))

    info = {k: v for k, v in probe_result.to_dict().items() if k != "hasAudio"}

    # F7 重复检测指纹：字节级精确指纹 + 中间帧感知哈希（复用抽帧结果）
    from .detectors import phash as phash_mod
    info["contentHash"] = phash_mod.file_sha256(local_path)
    if sampled:
        info["phash"] = phash_mod.dhash(sampled[len(sampled) // 2])

    return AnalysisOutcome(run_id=run_id, ok=True, worker_version=worker_version,
                           findings=findings, probe_info=info)


def _frame_checks(sampled, stamps) -> list[dict]:
    """黑帧/冻结检测（基于已抽好的帧）。"""
    frame_findings: list[dict] = []

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

    return frame_findings


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
