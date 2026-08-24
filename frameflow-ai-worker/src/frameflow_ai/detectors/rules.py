"""确定性规则判定：拿 profile spec 的 dimensions 对照 probe 结果。

核心原则（docs/01 §8）：
- 只有确定性维度可产生 BLOCKER（→ AUTO_REJECT）；
- 每条判定必须带证据（原始值 + 阈值）；
- spec 没配置的维度直接跳过（不检查不产生 Finding）。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional

from .probe import DETECTOR_VERSION, DetectorError, ProbeResult

DETECTOR_ID = "spec-rules"


@dataclass
class Finding:
    dimension: str
    passed: bool
    severity: str          # BLOCKER / WARNING / INFO
    evidence: dict = field(default_factory=dict)
    message: str = ""
    timecode_ms: Optional[int] = None

    def to_payload(self) -> dict:
        import json
        return {
            "detector": DETECTOR_ID,
            "detectorVersion": DETECTOR_VERSION,
            "dimension": self.dimension,
            "passed": self.passed,
            "severity": self.severity,
            "timecodeMs": self.timecode_ms,
            "evidence": json.dumps(self.evidence, ensure_ascii=False),
            "message": self.message,
        }


def evaluate(spec: dict, probe_result: ProbeResult) -> list[Finding]:
    """对照 spec.dimensions 逐项判定，返回 Finding 列表（含通过项）。"""
    dims = (spec or {}).get("dimensions", {}) or {}
    findings: list[Finding] = []

    duration_rule = dims.get("duration")
    if duration_rule:
        findings.append(_check_duration(duration_rule, probe_result))

    resolution_rule = dims.get("resolution")
    if resolution_rule:
        findings.append(_check_resolution(resolution_rule, probe_result))

    fps_rule = dims.get("fps")
    if fps_rule:
        findings.append(_check_fps(fps_rule, probe_result))

    return findings


def _severity_of(rule: dict, default: str = "BLOCKER") -> str:
    # 规则可显式声明 severity；确定性维度缺省 BLOCKER（可自动淘汰）
    return rule.get("severity", default)


def _check_duration(rule: dict, p: ProbeResult) -> Finding:
    minimum = rule.get("min")
    maximum = rule.get("max")   # 单位：秒
    if p.duration_ms is None:
        raise DetectorError("已启用 duration 规则，但 ffprobe 未提供可用时长")
    seconds = p.duration_ms / 1000
    ok = (
        (minimum is None or seconds >= minimum)
        and (maximum is None or seconds <= maximum)
    )
    return Finding(
        dimension="duration",
        passed=ok,
        severity=_severity_of(rule),
        evidence={"durationMs": p.duration_ms, "min": minimum, "max": maximum},
        message=f"时长 {seconds}s，要求 [{minimum},{maximum}]s",
    )


def _check_resolution(rule: dict, p: ProbeResult) -> Finding:
    min_w = rule.get("minWidth")
    min_h = rule.get("minHeight")
    if p.width is None or p.height is None:
        raise DetectorError("已启用 resolution 规则，但 ffprobe 未提供完整宽高")
    ok = (
        (min_w is None or p.width >= min_w)
        and (min_h is None or p.height >= min_h)
    )
    return Finding(
        dimension="resolution",
        passed=ok,
        severity=_severity_of(rule),
        evidence={"width": p.width, "height": p.height,
                  "minWidth": min_w, "minHeight": min_h},
        message=f"分辨率 {p.width}x{p.height}，最小要求 {min_w}x{min_h}",
    )


def _check_fps(rule: dict, p: ProbeResult) -> Finding:
    minimum = rule.get("min")
    if p.fps is None:
        raise DetectorError("已启用 fps 规则，但 ffprobe 未提供可用帧率")
    ok = minimum is None or p.fps >= minimum
    return Finding(
        dimension="fps",
        passed=ok,
        severity=_severity_of(rule),
        evidence={"fps": p.fps, "min": minimum},
        message=f"帧率 {p.fps}，要求 >= {minimum}",
    )
