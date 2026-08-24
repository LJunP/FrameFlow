#!/usr/bin/env python3
"""Render deterministic JSON + Markdown reports from F11 metric evidence.

Reading order: ``main`` -> ``build_report`` -> ``render_markdown``.  The report
always carries its data classification and never decides real pilot value.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
from typing import Any

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[2]))

from scripts.pilot.pilotlib import (  # noqa: E402
    CLASSIFICATION,
    PilotError,
    assert_no_owner_only_states,
    atomic_write_text,
    read_json,
    write_json,
)


def _format_rate(metric: dict[str, Any]) -> tuple[str, str, str]:
    value = metric.get("value")
    interval = metric.get("confidenceInterval")
    if value is None:
        return "N/A", "N/A", str(metric.get("reason") or "NO_VALUE")
    display = f"{float(value) * 100:.1f}%"
    if isinstance(interval, dict) and isinstance(interval.get("low"), (int, float)):
        interval_display = f"{float(interval['low']) * 100:.1f}%–{float(interval['high']) * 100:.1f}%"
    else:
        interval_display = "N/A"
    caution = "小样本" if metric.get("sampleSizeCaution") else "—"
    if metric.get("missingCount"):
        caution += f"；缺失 {metric['missingCount']}"
    return display, interval_display, caution


def build_report(
    metrics: dict[str, Any],
    validation: dict[str, Any],
    provenance: dict[str, Any],
    dataset_summary: dict[str, Any],
) -> dict[str, Any]:
    classification = metrics.get("classification")
    if classification not in {CLASSIFICATION, "REAL_PILOT"}:
        raise PilotError("unsupported report classification")
    for label, value in (
        ("metrics", metrics),
        ("validation", validation),
        ("provenance", provenance),
        ("dataset summary", dataset_summary),
    ):
        if value.get("classification") != classification:
            raise PilotError(f"{label} classification does not match metrics")
    if validation.get("status") != "PASS":
        raise PilotError("cannot report a package that did not pass validation")

    if classification == CLASSIFICATION:
        facts = [
            "本地已生成合成视频、关键帧图片、Brief、Profile、标注、重复/异常媒体与 300 记录压力 manifest。",
            "Package validator 已检查 hash、路径约束、盲评字段隔离、异常媒体 probe 与真实 Provider 禁用策略。",
            "指标包含零分母守卫与缺失计数；时长节省使用成对 bootstrap 区间，二元比例使用 Wilson 区间。",
            "300 记录压力批次只验证 manifest/工具容量；它没有上传到应用，也不是吞吐量测量。",
            "处理时长是合成场景输入，不是应用墙钟实测。",
        ]
        inferences = [
            "F11 本地数据契约、标注、指标与报告链已具备接收合规授权真实试点包的工程条件。",
            "非平凡合成比例和 Reviewer 分歧实际覆盖了警告/裁决路径，没有制造全绿结果。",
        ]
        unknowns = [
            "真实客户批次构成、权利/同意记录与留存义务。",
            "真实 Provider 的输出质量、延迟与成本；当前没有发生真实调用。",
            "Operator 实际观看行为、误杀率、交付后漏检率与端到端处理时长。",
            "真实试点是否能在足够分母下满足预注册成功阈值。",
        ]
        decision_reason = "SYNTHETIC_DATA_AND_NO_REAL_PROVIDER_OR_CUSTOMER_WORKFLOW"
    else:
        facts = [
            "Intake validator 对所给真实试点 manifest、引用 bytes 与授权元数据报告 PASS。",
            "指标以描述性方式计算并带零分母守卫和缺失计数；时长节省使用成对 bootstrap，二元比例使用 Wilson 区间。",
            "生成器不修改标签、不插补缺失、不调阈值，也不代替所有者作价值判断。",
        ]
        inferences = [
            "若盲评完整性与抽样假设同样成立，Evidence 包在技术上可交所有者审阅。",
        ]
        unknowns = [
            "预注册阈值与最小分母是否满足；必须对照已批准试点方案判断。",
            "Manifest、标注和事件日志未编码的运营或定性背景。",
        ]
        decision_reason = "OWNER_REVIEW_AND_PRE_REGISTERED_THRESHOLD_ASSESSMENT_REQUIRED"

    result = {
        "schemaVersion": "frameflow.pilot-report.v1",
        "classification": classification,
        "status": f"{classification}_DESCRIPTIVE_REPORT_COMPLETE",
        "decision": {
            "realPilotValueDecisionEligible": False,
            "reason": decision_reason,
            "ownerApprovalRequired": True,
        },
        "provenance": {
            "rehearsalId": provenance.get("rehearsalId"),
            "datasetId": dataset_summary.get("datasetId"),
            "seed": dataset_summary.get("seed"),
            "sourceCommit": provenance.get("source", {}).get("gitCommit"),
            "inputSetSha256": provenance.get("inputSetSha256"),
        },
        "facts": facts,
        "inferences": inferences,
        "unknowns": unknowns,
        "metrics": metrics.get("metrics"),
        "sampleLimitations": metrics.get("sampleLimitations"),
        "validation": {
            "status": validation.get("status"),
            "counts": validation.get("counts"),
        },
        "dataset": dataset_summary,
    }
    return result


def render_markdown(report: dict[str, Any]) -> str:
    metrics = report["metrics"]
    rows: list[tuple[str, dict[str, Any]]] = [
        ("人工观看时长节省率（主指标）", metrics["manualViewingTimeSavingsRate"]),
        ("完整观看规避率（辅助）", metrics["fullWatchAvoidanceRate"]),
        ("误杀率", metrics["falseKillRate"]),
        ("漏检率", metrics["missRate"]),
    ]
    synthetic = report["classification"] == CLASSIFICATION
    title = (
        "# SYNTHETIC_REHEARSAL · F11 合成试点彩排报告"
        if synthetic
        else "# REAL_PILOT · F11 描述性指标报告"
    )
    boundary = (
        "> **边界：这是合成数据工程彩排，不是现实试点，也不具备真实价值结论资格。**"
        if synthetic
        else "> **边界：这是描述性统计报告；最终价值判断仍由所有者按预注册方案审批。**"
    )
    policy_line = (
        "> 本次没有客户媒体、个人数据或真实 Provider 调用。"
        if synthetic
        else "> 生成器不回显媒体、客户身份、Provider 配置或密钥。"
    )
    lines = [
        title,
        "",
        boundary,
        policy_line,
        "",
        "## Provenance",
        "",
        f"- rehearsalId：`{report['provenance']['rehearsalId']}`",
        f"- datasetId：`{report['provenance']['datasetId']}`",
        f"- seed：`{report['provenance']['seed']}`",
        f"- source commit：`{report['provenance']['sourceCommit']}`",
        f"- input set SHA-256：`{report['provenance']['inputSetSha256']}`",
        "",
        "## FACT（已由本地 Evidence 验证）",
        "",
    ]
    lines.extend(f"- {fact}" for fact in report["facts"])
    lines.extend(
        [
            "",
            "## 合成指标（只验证计算口径）",
            "",
            "| 指标 | 值 | 95% 区间 | 统计基数 | 限制 |",
            "|---|---:|---:|---:|---|",
        ]
    )
    for label, metric in rows:
        value, interval, caution = _format_rate(metric)
        if "baselineSeconds" in metric:
            basis = f"{metric.get('savedSeconds')}/{metric.get('baselineSeconds')} 秒"
        else:
            basis = f"{metric['numerator']}/{metric['denominator']}"
        lines.append(
            f"| {label} | {value} | {interval} | {basis} | {caution} |"
        )
    agreement = metrics["blindReviewAgreement"]
    kappa = agreement.get("cohenKappa")
    kappa_display = "N/A" if kappa is None else f"{float(kappa):.3f}"
    raw = agreement.get("rawAgreement")
    raw_display = "N/A" if raw is None else f"{float(raw) * 100:.1f}%"
    timing = metrics["batchProcessingTime"]
    lines.extend(
        [
            "",
            f"- 盲评一致性：raw agreement `{raw_display}`，Cohen's κ `{kappa_display}`，"
            f"可比样本 `{agreement['overlapCount']}`。",
            f"- 处理时长中位数：`{timing.get('medianSeconds')}` 秒"
            + ("（合成输入，不是应用实测）。" if synthetic else "。"),
            "- 分母低于 30 的比例均带小样本警告；逐项以机器报告为准。",
            "",
            "## INFERENCE（工程推断）",
            "",
        ]
    )
    lines.extend(f"- {item}" for item in report["inferences"])
    lines.extend(["", "## UNKNOWN（真实试点仍待验证）", ""])
    lines.extend(f"- {item}" for item in report["unknowns"])
    lines.extend(
        [
            "",
            "## 复现",
            "",
            "```bash",
            "python3 scripts/pilot/run_synthetic_rehearsal.py",
            "python3 -m unittest discover -s tests/pilot -v",
            "```",
            "",
            "完整逐文件 SHA-256 与执行环境见同目录 `receipt.json`、`provenance.json` 和",
            "`artifact-checksums.sha256`。",
            "",
        ]
    )
    markdown = "\n".join(lines)
    assert_no_owner_only_states(markdown, "Markdown report")
    return markdown


def write_reports(
    metrics: dict[str, Any],
    validation: dict[str, Any],
    provenance: dict[str, Any],
    dataset_summary: dict[str, Any],
    json_output: Path,
    markdown_output: Path,
) -> dict[str, Any]:
    report = build_report(metrics, validation, provenance, dataset_summary)
    markdown = render_markdown(report)
    write_json(json_output, report)
    assert_no_owner_only_states(json_output.read_text(encoding="utf-8"), "JSON report")
    atomic_write_text(markdown_output, markdown)
    return report


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--metrics", type=Path, required=True)
    parser.add_argument("--validation", type=Path, required=True)
    parser.add_argument("--provenance", type=Path, required=True)
    parser.add_argument("--dataset-summary", type=Path, required=True)
    parser.add_argument("--json-output", type=Path, required=True)
    parser.add_argument("--markdown-output", type=Path, required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        write_reports(
            read_json(args.metrics),
            read_json(args.validation),
            read_json(args.provenance),
            read_json(args.dataset_summary),
            args.json_output,
            args.markdown_output,
        )
    except PilotError as exc:
        raise SystemExit(f"pilot report generation failed: {exc}") from exc
    print(args.markdown_output)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
