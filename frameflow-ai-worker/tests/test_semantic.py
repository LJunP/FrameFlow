from frameflow_ai.semantic import FakeSemanticProvider, build_registry, provider_spec


def test_fake_provider_pass_violate_unknown_error():
    p = FakeSemanticProvider()
    ctx = {"mediaAvailable": True}
    assert p.evaluate({"assertionId": "a1", "expectedVerdict": "PASS"}, ctx).verdict == "PASS"
    assert p.evaluate({"assertionId": "a2", "expectedVerdict": "VIOLATE"}, ctx).verdict == "VIOLATE"
    assert p.evaluate({"assertionId": "a3", "expectedVerdict": "UNKNOWN"}, ctx).verdict == "UNKNOWN"
    assert p.evaluate({"assertionId": "a4", "expectedVerdict": "ERROR"}, ctx).verdict == "ERROR"


def test_fake_provider_forced_error():
    p = FakeSemanticProvider()
    assert p.evaluate({"assertionId": "a1", "forcedOutcome": "ERROR"}, {}).verdict == "ERROR"


def test_registry_and_spec():
    reg = build_registry()
    assert "fake" in reg
    spec = provider_spec()
    assert spec["providers"]["fake"]["requiresSecret"] is False
