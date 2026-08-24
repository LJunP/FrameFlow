"""语义编排：抽关键帧 → 组请求 → 调 Provider → 组装带证据束的 Finding。

★ 红线（docs/01 §8.2）的 Python 侧落点：
- Provider 失败/禁用 → 语义 ERROR Finding（severity=WARNING，verdict=ERROR）
  → Java 端进 REVIEW_REQUIRED；任务本身绝不能因此失败；
- 所有语义 Finding 一律 WARNING/INFO，永不 BLOCKER。
"""

from __future__ import annotations

import hashlib
import json
import logging
import os

from .model_catalog import (ModelCatalogError, ModelSelectionError,
                            load_model_catalog)
from .providers import (FakeProvider, OpenAICompatProvider,
                        OpenAIResponsesProvider, ProviderDisabled,
                        ProviderError, SemanticProvider, SemanticRequest)

log = logging.getLogger(__name__)

DEFAULT_DIMENSIONS = ["prompt_alignment", "quality_impression", "policy_violation"]
MAX_KEYFRAMES = 3          # 预算：一次语义调用最多携带的关键帧数
ROUTER_PROVIDER_NAME = "semantic-model-router"


def build_provider(model_id: object = None) -> SemanticProvider:
    """按平台目录路由 Provider；Fake 只能由显式演示/测试配置启用。"""
    # ★ 核心：缺少真实 Provider 凭据必须形成可观测的 ERROR Finding，不能
    # 静默切到伪判定；否则用户会把稳定哈希生成的结果误认为模型看过视频。
    if os.environ.get("FRAMEFLOW_SEMANTIC_PROVIDER", "").strip().lower() == "fake":
        return FakeProvider()

    # ★ 核心：modelId 只在平台下发的 enabled 白名单中解析。未知、禁用或
    # 目录损坏都抛给编排层形成 ERROR，绝不偷偷换成 default/legacy/Fake。
    selected = load_model_catalog().select(model_id)
    # ★ 核心：provider 字段决定 wire protocol；即使 baseUrl 与 model 相同，
    # Chat Completions 和 Responses 的路径、图片块与输出结构也不能混用。
    provider_type = {
        "openai-compat": OpenAICompatProvider,
        "openai-responses": OpenAIResponsesProvider,
    }.get(selected.provider)
    if provider_type is None:
        # 正常目录解析已在更早处拒绝未知值；这里保留纵深防御，避免未来
        # 绕过解析器的调用静默落到错误协议。
        raise ModelCatalogError("模型目录 provider 不受支持")
    return provider_type(
        base_url=selected.base_url,
        api_key=os.environ.get(selected.api_key_env, ""),
        model=selected.model,
        model_id=selected.id,
    )


def run_semantic(spec: dict, brief_content: str, video_path: str,
                 sampled_frames, stamps_ms, provider: SemanticProvider | None,
                 candidate_hint: str = "") -> list[dict]:
    """返回语义 Finding 列表（payload 形态，可并入 pipeline 结果）。"""
    semantic_cfg = (spec or {}).get("semantic") or {}
    if not semantic_cfg.get("enabled"):
        return []
    # ★ 核心：字段缺失才使用兼容默认值；显式 [] 代表“不检查任何语义维度”。
    # 若把空数组当假值回退，会悄悄重新启用用户已取消的全部检查。
    dimensions = (DEFAULT_DIMENSIONS if "dimensions" not in semantic_cfg
                  else semantic_cfg.get("dimensions"))
    if not dimensions:
        return []
    requested_model_id = semantic_cfg.get("modelId")
    try:
        provider = provider or build_provider(requested_model_id)
    except ModelCatalogError as e:
        log.warning("语义模型目录无效: %s", e)
        return _error_finding(
            dimensions, f"模型目录配置无效: {e}", ROUTER_PROVIDER_NAME,
            _safe_model_id(requested_model_id), None)
    except ModelSelectionError as e:
        log.warning("语义模型选择失败: %s", e)
        return _error_finding(
            dimensions, str(e), ROUTER_PROVIDER_NAME,
            e.model_id, e.actual_model)

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
            "modelId": result.model_id,
            "model": result.model,
            "prompt": result.prompt_snapshot,
            "rawOutput": result.raw_output,
            "keyframeTimecodesMs": stamps,
            "keyframeSha256": [hashlib.sha256(jpeg).hexdigest() for jpeg in jpegs],
        }
        return [_finding(v, evidence) for v in result.verdicts]
    except ProviderDisabled as e:
        log.warning("语义 Provider 禁用: %s", e)
        return _error_finding(
            dimensions, str(e), provider.name,
            getattr(provider, "model_id", _safe_model_id(requested_model_id)),
            getattr(provider, "model", None))
    except ProviderError as e:
        log.warning("语义 Provider 失败: %s", e)
        return _error_finding(
            dimensions, str(e), provider.name,
            getattr(provider, "model_id", _safe_model_id(requested_model_id)),
            getattr(provider, "model", None))


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


def _error_finding(dimensions: list[str], reason: str, provider_name: str,
                   model_id: str | None = None,
                   actual_model: str | None = None) -> list[dict]:
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
            {"provider": provider_name, "modelId": model_id,
             "model": actual_model, "error": reason}, ensure_ascii=False),
        "message": f"语义 Provider 不可用: {reason[:200]}",
    } for dim in dimensions]


def _safe_model_id(value: object) -> str | None:
    """只把契约允许的字符串 ID 放进证据，避免序列化任意输入对象。"""
    return value if isinstance(value, str) and value else None


def _to_jpeg(frame) -> bytes:
    import cv2
    ok, buf = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 80])
    if not ok:
        raise ProviderError("关键帧编码失败")
    return buf.tobytes()
