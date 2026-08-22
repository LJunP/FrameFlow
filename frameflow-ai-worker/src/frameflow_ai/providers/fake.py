"""FakeProvider：确定性伪语义分析——评测与全链路测试的地基。

★ 可复现性：判定由 (object_hint, dimension) 的稳定哈希决定，
同输入永远同输出——评测报告才能复现（docs/03 F6 完成标准）。
"""

from __future__ import annotations

import hashlib

from .base import SemanticRequest, SemanticResult, SemanticVerdict


class FakeProvider:
    name = "semantic-fake"
    version = "1"

    def analyze(self, request: SemanticRequest) -> SemanticResult:
        verdicts = []
        for dim in request.dimensions:
            digest = hashlib.sha256(
                f"{request.candidate_hint}:{dim}:{request.brief_content}".encode()).hexdigest()
            bucket = int(digest[:8], 16) % 10
            if bucket < 6:
                v, reason = "PASS", "伪判定：与 brief 大致对齐"
            elif bucket < 8:
                v, reason = "VIOLATE", "伪判定：疑似语义违反"
            else:
                v, reason = "UNKNOWN", "伪判定：模型不确定"
            verdicts.append(SemanticVerdict(
                dimension=dim, verdict=v, reason=reason,
                timecode_ms=request.frame_timecodes_ms[0]
                if request.frame_timecodes_ms else None))
        prompt = (f"[fake] brief={request.brief_content[:80]} "
                  f"dimensions={request.dimensions}")
        return SemanticResult(verdicts=verdicts, prompt_snapshot=prompt,
                              raw_output="[fake-provider deterministic output]",
                              provider=self.name, provider_version=self.version)
