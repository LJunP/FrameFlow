"""Quality profile application, eligibility gate and decision rules.

Hard constraints stay separate from ranking. The gate engine:
  - never auto-rejects on UNKNOWN;
  - never auto-rejects semantic assertions (default REVIEW per BOOK-03);
  - only deterministic HARD_CONSTRAINT / DETECTOR_THRESHOLD rules that are
    explicitly marked REJECT (or are BLOCKER) can produce an automatic REJECT.
"""
from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Decision:
    value: str  # ELIGIBLE | REVIEW | REJECT | ANALYSIS_ERROR
    automatic: bool
    reasons: list[str] = field(default_factory=list)


def _rid(f) -> str:
    return f.get("ruleId") or str(f.get("rule_id"))


def _verdict(f) -> str:
    return str(f.get("verdict") or "").upper()


def _severity(f) -> str:
    return str(f.get("severity") or "INFO").upper()


def _is_semantic_rule(rule) -> bool:
    return bool(rule) and rule.get("type") in {"SEMANTIC_ASSERTION", "PROHIBITED_ASSERTION"}


def apply_profile(profile: dict, findings: list, profile_digest: str | None = None) -> Decision:
    rules = {r.get("ruleId"): r for r in profile.get("rules", [])}
    automation = profile.get("automationPolicy", {})
    semantic_auto_reject = automation.get("semanticAutoRejectEnabled", False)
    unknown_action = automation.get("unknownResultAction", "REVIEW")

    reasons: list[str] = []
    for finding in findings:
        rule = rules.get(_rid(finding))
        verdict = _verdict(finding)
        severity = _severity(finding)
        rim = _rid(finding)
        if verdict == "VIOLATED":
            if _is_semantic_rule(rule):
                if semantic_auto_reject:
                    reasons.append("auto-reject " + rim + " @ " + severity + " (semantic auto-reject enabled)")
                else:
                    reasons.append("semantic " + rim + " -> review")
            elif rule and rule.get("automationAction") == "REJECT":
                reasons.append("auto-reject " + rim + " @ " + severity + " (deterministic/eligible)")
            elif severity == "BLOCKER":
                reasons.append("block " + rim + " @ " + severity)
            else:
                reasons.append("finding " + rim + " -> review")
        elif verdict == "UNKNOWN" and unknown_action == "REVIEW":
            reasons.append("unknown " + rim + " -> review")

    rejectable = [reason for reason in reasons
                  if reason.startswith("auto-reject ") or reason.startswith("block ")]
    if rejectable:
        return Decision("REJECT", True, rejectable)
    reviewable = [reason for reason in reasons if "review" in reason]
    if reviewable:
        return Decision("REVIEW", False, reviewable)
    return Decision("ELIGIBLE", False, ["no blocking findings"])


def analyze_error() -> Decision:
    return Decision("ANALYSIS_ERROR", False, ["analysis failed; not a quality judgment"])
