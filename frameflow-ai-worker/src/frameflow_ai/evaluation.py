"""离线评测：拿标注数据集跑 Provider，输出可复现的指标报告。

指标定义（docs/01 §10 的语义侧投影）：
- accuracy      ：判定与标注一致的比例
- false_reject  ：误杀率——标注 PASS 但被判 VIOLATE（本产品最痛的错）
- needs_review  ：UNKNOWN/ERROR 占比（转人工的成本）
评测集固定、Provider 确定性 → 报告可复现；换 Provider/换 prompt 后重跑
对比指标，就是"评测驱动调参"的闭环（防止对着线上数据调参凑指标）。
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class EvalCase:
    id: str
    brief: str
    candidate_hint: str
    expected: dict          # dimension -> PASS/VIOLATE/UNKNOWN


@dataclass
class EvalReport:
    provider: str
    version: str
    total_cases: int
    accuracy: float
    false_reject_rate: float
    needs_review_rate: float
    mismatches: list

    def to_dict(self) -> dict:
        return self.__dict__.copy()


def load_dataset(path: str | Path) -> list[EvalCase]:
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    return [EvalCase(c["id"], c["brief"], c["candidateHint"], c["expected"])
            for c in data["cases"]]


def evaluate(provider, cases: list[EvalCase]) -> EvalReport:
    """跑全部用例并计算指标（不写任何外部状态，纯函数式可复现）。"""
    from .providers.base import SemanticRequest

    total = 0
    correct = 0
    false_rejects = 0
    review = 0
    verdict_count = 0
    mismatches = []

    for case in cases:
        request = SemanticRequest(
            brief_content=case.brief, frames_jpeg=[],
            frame_timecodes_ms=[0], dimensions=list(case.expected.keys()),
            candidate_hint=case.candidate_hint)
        result = provider.analyze(request)
        got = {v.dimension: v.verdict for v in result.verdicts}
        for dim, expected in case.expected.items():
            total += 1
            verdict_count += 1
            actual = got.get(dim, "UNKNOWN")
            if actual == expected:
                correct += 1
            else:
                mismatches.append({"case": case.id, "dimension": dim,
                                   "expected": expected, "actual": actual})
            if expected == "PASS" and actual == "VIOLATE":
                false_rejects += 1
            if actual in ("UNKNOWN", "ERROR"):
                review += 1

    return EvalReport(
        provider=provider.name, version=provider.version,
        total_cases=len(cases),
        accuracy=round(correct / total, 4) if total else 0.0,
        false_reject_rate=round(false_rejects / total, 4) if total else 0.0,
        needs_review_rate=round(review / verdict_count, 4) if verdict_count else 0.0,
        mismatches=mismatches)


def main() -> None:
    import sys
    dataset = sys.argv[1] if len(sys.argv) > 1 else "eval/dataset.json"
    from .semantic import build_provider
    provider = build_provider()
    report = evaluate(provider, load_dataset(dataset))
    print(json.dumps(report.to_dict(), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
