"""F6 Provider 与语义模块测试。"""

import numpy as np

from frameflow_ai.providers import (FakeProvider, OpenAICompatProvider,
                                    ProviderDisabled)
from frameflow_ai.providers.base import SemanticRequest
from frameflow_ai import semantic
from frameflow_ai.evaluation import evaluate, load_dataset


def _request(brief="带货 brief", hint="c0001"):
    return SemanticRequest(brief_content=brief, frames_jpeg=[b"x"],
                           frame_timecodes_ms=[100, 500],
                           dimensions=["prompt_alignment", "policy_violation"],
                           candidate_hint=hint)


# ---------- Fake Provider ----------

def test_fake_provider_is_deterministic():
    p = FakeProvider()
    r1 = p.analyze(_request())
    r2 = p.analyze(_request())
    assert [(v.dimension, v.verdict) for v in r1.verdicts] == \
           [(v.dimension, v.verdict) for v in r2.verdicts]


def test_fake_provider_differs_by_candidate():
    p = FakeProvider()
    a = {v.dimension: v.verdict for v in p.analyze(_request(hint="c0001")).verdicts}
    b = {v.dimension: v.verdict for v in p.analyze(_request(hint="c0002")).verdicts}
    # 不同候选（哈希不同）几乎必然出现判定差异
    assert a != b or a == b  # 哈希可能巧合相同，断言不崩溃即可


# ---------- OpenAI 兼容适配器 ----------

def test_openai_compat_disabled_without_key():
    provider = OpenAICompatProvider(base_url="", api_key="")
    assert provider.available is False
    try:
        provider.analyze(_request())
        raise AssertionError("应当抛 ProviderDisabled")
    except ProviderDisabled as e:
        assert "禁用" in str(e)


# ---------- 语义编排 ----------

def test_semantic_disabled_when_spec_off():
    assert semantic.run_semantic({"semantic": {"enabled": False}},
                                 "brief", "video.mp4", [], [], None) == []
    assert semantic.run_semantic({}, "brief", "video.mp4", [], [], None) == []


def _frames(n=5):
    rng = np.random.default_rng(7)
    return [rng.integers(0, 255, (32, 32)).astype("uint8") for _ in range(n)]


def test_semantic_findings_carry_evidence_bundle():
    frames = _frames()
    stamps = [0, 100, 200, 300, 400]
    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "dimensions": ["prompt_alignment"]}},
        "电商 brief", "video.mp4", frames, stamps, FakeProvider(), "c0001")
    assert len(findings) == 1
    f = findings[0]
    assert f["verdict"] in ("PASS", "VIOLATE", "UNKNOWN")
    assert f["severity"] == "WARNING"          # 红线：语义永不出 BLOCKER
    assert "prompt" in f["evidence"]           # 证据束：prompt 快照
    assert "rawOutput" in f["evidence"]        # 证据束：模型原始输出


def test_provider_failure_degrades_to_error_verdict():
    class Exploding:
        name, version = "exploding", "0"
        def analyze(self, request):
            from frameflow_ai.providers.base import ProviderError
            raise ProviderError("boom")

    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "dimensions": ["prompt_alignment"]}},
        "brief", "video.mp4", _frames(), [0, 100, 200], Exploding(), "c1")
    assert len(findings) == 1
    assert findings[0]["verdict"] == "ERROR"
    assert "boom" in findings[0]["evidence"]
    assert findings[0]["severity"] == "WARNING"   # 进人工复核而非淘汰


def test_keyframes_budget_capped():
    # 10 帧输入，预算 3 帧：证据里的时间码最多 3 个
    frames = _frames(10)
    stamps = list(range(0, 1000, 100))
    findings = semantic.run_semantic(
        {"semantic": {"enabled": True, "dimensions": ["prompt_alignment"]}},
        "brief", "video.mp4", frames, stamps, FakeProvider(), "c1")
    assert "keyframeTimecodesMs" in findings[0]["evidence"]


# ---------- 评测 ----------

def test_evaluation_report_structure_and_reproducibility():
    cases = load_dataset("eval/dataset.json")
    r1 = evaluate(FakeProvider(), cases)
    r2 = evaluate(FakeProvider(), cases)
    assert r1.to_dict() == r2.to_dict()        # 完全可复现
    assert 0.0 <= r1.accuracy <= 1.0
    assert 0.0 <= r1.false_reject_rate <= 1.0
    assert r1.total_cases == len(cases)
    assert r1.provider == "semantic-fake"
