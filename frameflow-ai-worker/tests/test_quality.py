from frameflow_ai.quality import apply_profile


def _finding(rid, verdict, severity="INFO", dim="technical_quality"):
    return {"ruleId": rid, "detectorId": "det", "detectorVersion": "1.0.0",
            "dimension": dim, "verdict": verdict, "severity": severity,
            "confidence": 1.0, "summary": "test"}


def test_reject_on_deterministic_blocker():
    profile = {"rules": [{"ruleId": "R1", "automationAction": "REJECT"}],
               "automationPolicy": {"unknownResultAction": "REVIEW"}}
    d = apply_profile(profile, [_finding("R1", "VIOLATED", "BLOCKER")])
    assert d.value == "REJECT"
    assert d.automatic is True


def test_unknown_never_auto_rejects():
    profile = {"rules": [{"ruleId": "R1", "automationAction": "REJECT"}],
               "automationPolicy": {"semanticAutoRejectEnabled": False, "unknownResultAction": "REVIEW"}}
    d = apply_profile(profile, [_finding("R1", "UNKNOWN"), _finding("R2", "UNKNOWN")])
    assert d.value == "REVIEW"


def test_semantic_review_default():
    profile = {"rules": [{"ruleId": "S1", "automationAction": "REVIEW"}],
               "automationPolicy": {"semanticAutoRejectEnabled": False, "unknownResultAction": "REVIEW"}}
    d = apply_profile(profile, [_finding("S1", "VIOLATED", "MAJOR", "prompt_alignment")])
    assert d.value == "REVIEW"


def test_eligible_when_no_findings():
    profile = {"rules": [], "automationPolicy": {"unknownResultAction": "REVIEW"}}
    d = apply_profile(profile, [])
    assert d.value == "ELIGIBLE"
