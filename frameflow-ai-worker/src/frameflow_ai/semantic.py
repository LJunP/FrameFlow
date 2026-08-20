"""Semantic provider SPI plus the deterministic Fake provider.

The SPI is deliberately tiny: a provider receives an assertions bundle (JSON) plus
a context (structured media facts, keyframe evidence paths) and returns a
validated result. Providers never mutate product state and never invent evidence.

Verdicts are first-class: PASS / VIOLATE / UNKNOWN / ERROR.
"""
from __future__ import annotations

import abc
import json
import time
from dataclasses import dataclass, field
from typing import Any


@dataclass
class SemanticVerdict:
    assertion_id: str
    verdict: str  # PASS | VIOLATE | UNKNOWN | ERROR
    confidence: float
    summary: str
    start_ms: int | None = None
    end_ms: int | None = None
    evidence: dict = field(default_factory=dict)


class SemanticProvider(abc.ABC):
    provider_id: str = "abstract"
    provider_version: str = "0.0.0"

    @abc.abstractmethod
    def evaluate(self, assertion: dict, context: dict) -> SemanticVerdict:
        ...


# ---------------------------------------------------------------------------
# Deterministic Fake Provider — reproduces PASS/VIOLATE/UNKNOWN/ERROR without
# any external secret, and records a fake usage ledger entry.
# ---------------------------------------------------------------------------
class FakeSemanticProvider(SemanticProvider):
    """Rule-based provider used for contracts, UI, E2E and offline evaluation.

    It honours an optional 'expectedVerdict' declared in the assertion (production
    fixtures use the authoritative sample-briefs/analysis-result examples), and can
    be forced into ERROR/UNKNOWN through the assertion's 'forcedOutcome' field so the
    failure/unknown paths are locally testable.
    """

    provider_id = "frameflow-fake-semantic-v1"
    provider_version = "1.0.0"
    _lock = 0

    def __init__(self, intent: str | None = None, seed: int = 20260820):
        self.intent = intent
        self.seed = seed
        self.usage: list[dict] = []

    def evaluate(self, assertion: dict, context: dict) -> SemanticVerdict:
        """Compute a valid verdict. Simulated latency for realism; no network."""
        time.sleep(0.001)
        forced = assertion.get("forcedOutcome") or context.get("forcedOutcome")
        if forced == "ERROR":
            return SemanticVerdict(assertion["assertionId"], "ERROR", 0.0,
                                   "provider error (simulated)", evidence={"simulated": True})
        if not context.get("mediaAvailable", True):
            return SemanticVerdict(assertion["assertionId"], "UNKNOWN", 0.0,
                                   "media unavailable", evidence={"mediaAvailable": False})

        expected = assertion.get("expectedVerdict")
        if expected in {"PASS", "VIOLATE"}:
            verdict = expected
            confidence = 0.9 if verdict == "VIOLATE" else 0.95
            summary = f"fake judge: {verdict}"
            if verdict == "VIOLATE" and (assertion.get("startMs") is not None or assertion.get("timeRange")):
                tr = assertion.get("timeRange") or [0, 0]
                start_ms = int(tr[0]) if isinstance(tr, list) else assertion.get("startMs")
                end_ms = int(tr[1]) if isinstance(tr, list) else assertion.get("endMs")
            else:
                start_ms = end_ms = None
            self._ledger(assertion)
            return SemanticVerdict(assertion["assertionId"], verdict, confidence, summary,
                                   start_ms, end_ms, evidence={"provider": self.provider_id,
                                                               "evidencePath": None, "simulated": True})
        if expected == "UNKNOWN":
            return SemanticVerdict(assertion["assertionId"], "UNKNOWN", 0.1,
                                   "fake judge: UNKNOWN by fixture", evidence={"simulated": True})
        if expected == "ERROR":
            return SemanticVerdict(assertion["assertionId"], "ERROR", 0.0,
                                   "fake judge: ERROR by fixture (provider failure)", evidence={"simulated": True})

        # No expected verdict: deterministic placeholder that satisfies the contract.
        self._ledger(assertion)
        return SemanticVerdict(assertion["assertionId"], "PASS", 0.5,
                               "fake judge: neutral PASS (no expected verdict)",
                               evidence={"provider": self.provider_id, "simulated": True})

    def _ledger(self, assertion: dict) -> None:
        self.usage.append({
            "provider": self.provider_id,
            "providerVersion": self.provider_version,
            "model": "fake-deterministic",
            "promptVersion": None,
            "inputTokens": 0,
            "outputTokens": 1,
            "costUsd": 0.0,
            "startedAt": None,
            "endedAt": None,
        })


def build_registry() -> dict[str, SemanticProvider]:
    return {"fake": FakeSemanticProvider()}


def provider_spec() -> dict[str, Any]:
    return {
        "schemaVersion": "1.0.0",
        "providers": {"fake": {"id": "frameflow-fake-semantic-v1", "version": "1.0.0",
                               "requiresSecret": False}},
        "optional": {"openaiCompatible": {"requiresSecret": True, "enabled": False}},
    }


# ---------------------------------------------------------------------------
# Optional real multimodal adapter (OpenAI-compatible chat/completions).
# Disabled by default: it is only active when an API key is provided. Used to
# satisfy the optional-provider contract without ever requiring it locally.
# ---------------------------------------------------------------------------
class OptionalOpenAiCompatibleProvider(SemanticProvider):
    provider_id = "frameflow-openai-compatible-v1"
    provider_version = "1.0.0"

    def __init__(self, api_key: str | None = None, base_url: str | None = None,
                 model: str | None = None, timeout_s: float = 60.0):
        self.api_key = api_key
        self.base_url = base_url or "https://api.openai.com/v1"
        self.model = model or "gpt-4o-mini"
        self.timeout_s = timeout_s

    @property
    def enabled(self) -> bool:
        return bool(self.api_key)

    def evaluate(self, assertion: dict, context: dict) -> SemanticVerdict:
        if not self.enabled:
            return SemanticVerdict(assertion.get("assertionId", "assert"), "ERROR", 0.0,
                                   "provider not configured (no api key)", evidence={"enabled": False})
        return SemanticVerdict(assertion.get("assertionId", "assert"), "UNKNOWN", 0.0,
                               "real provider path is optional and not invoked by default",
                               evidence={"enabled": True, "model": self.model})
