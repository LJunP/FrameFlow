"""语义编排：抽关键帧 → 组请求 → 调 Provider → 组装带证据束的 Finding。

★ 红线（docs/01 §8.2）的 Python 侧落点：
- Provider 失败/禁用 → 语义 ERROR Finding（severity=WARNING，verdict=ERROR）
  → Java 端进 REVIEW_REQUIRED；任务本身绝不能因此失败；
- 所有语义 Finding 一律 WARNING/INFO，永不 BLOCKER。
"""

from __future__ import annotations

import json
import logging

from .providers import (FakeProvider, OpenAICompatProvider, ProviderDisabled,
                        ProviderError, SemanticProvider, SemanticRequest)

log = logging.getLogger(__name__)

DEFAULT_DIMENSIONS = ["prompt_alignment", "quality_impression", "policy_violation"]
MAX_KEYFRAMES = 3          # 预算：一次语义调用最多携带的关键帧数


def build_provider() -> SemanticProvider:
    """按环境选择 Provider：有配置用真实适配器，否则 Fake（可测/可演示）。"""
    real = OpenAICompatProvider()
    if real.available:
        return real
    return FakeProvider()


def run_semantic(spec: dict, brief_content: str, video_path: str,
                 sampled_frames, stamps_ms, provider: SemanticProvider | None,
                 candidate_hint: str = "") -> list[dict]:
    """返回语义 Finding 列表（payload 形态，可并入 pipeline 结果）。"""
    semantic_cfg = (spec or {}).get("semantic") or {}
    if not semantic_cfg.get("enabled"):
        return []
    provider = provider or build_provider()
    dimensions = semantic_cfg.get("dimensions") or DEFAULT_DIMENSIONS

    # 关键帧预算：均匀取至多 MAX_KEYFRAMES 帧（多帧 ≠ 更准，只 = 更贵）
    step = max(1, len(sampled_frames) // MAX_KEYFRAMES) if sampled_frames else 1
    frames = sampled_frames[::step][:MAX_KEYFRAMES]
    stamps = stamps_ms[::step][:MAX_KEYFRAMES]
    jpegs = [_to_jpeg(f) for f in frames]

    request = SemanticRequest(
        brief_content=brief_content, frames_jpeg=jpegs,
        frame_timecodes_ms=stamps, dimensions=dimensions,
        candidate_hint=candidate_hint)

    try:
        result = provider.analyze(request)
        # ★ 证据束：prompt 快照 + 原始输出 + 关键帧时间码全部入 evidence——
        # 任何语义结论都能还原"当时模型看到了什么、问了什么、答了什么"
        evidence = {
            "provider": result.provider,
            "providerVersion": result.provider_version,
            "prompt": result.prompt_snapshot,
            "rawOutput": result.raw_output,
            "keyframeTimecodesMs": stamps,
        }
        return [_finding(v, evidence) for v in result.verdicts]
    except ProviderDisabled as e:
        log.warning("语义 Provider 禁用: %s", e)
        return _error_finding(dimensions, str(e), provider.name)
    except ProviderError as e:
        log.warning("语义 Provider 失败: %s", e)
        return _error_finding(dimensions, str(e), provider.name)


def _finding(v, evidence: dict) -> dict:
    return {
        "detector": "semantic",
        "detectorVersion": "1",
        "dimension": v.dimension,
        "passed": v.verdict == "PASS",
        "severity": "WARNING",          # 语义永不出 BLOCKER（红线）
        "verdict": v.verdict,
        "timecodeMs": v.timecode_ms,
        "evidence": json.dumps({**evidence, "reason": v.reason}, ensure_ascii=False),
        "message": f"[{v.verdict}] {v.reason[:200]}",
    }


def _error_finding(dimensions: list[str], reason: str, provider_name: str) -> dict:
    # 逐维度产出 ERROR 判定（每条都进人工复核，而不是笼统一句失败）
    return [{
        "detector": "semantic",
        "detectorVersion": "1",
        "dimension": dim,
        "passed": False,
        "severity": "WARNING",
        "verdict": "ERROR",
        "timecodeMs": None,
        "evidence": json.dumps(
            {"provider": provider_name, "error": reason}, ensure_ascii=False),
        "message": f"语义 Provider 不可用: {reason[:200]}",
    } for dim in dimensions]


def _to_jpeg(frame) -> bytes:
    import cv2
    ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
    if not ok:
        raise ProviderError("关键帧编码失败")
    return buf.tobytes()
