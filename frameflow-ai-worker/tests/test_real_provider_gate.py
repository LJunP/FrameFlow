"""受控真实 Provider 门禁执行器测试；所有网络均为离线 Stub。"""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import sys

import pytest


REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "scripts" / "run_real_provider_gate.py"


def _load_gate_module():
    spec = importlib.util.spec_from_file_location("frameflow_real_provider_gate", SCRIPT)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_dry_run_proves_visual_challenge_without_network(tmp_path):
    evidence = tmp_path / "evidence"
    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "--mode", "dry-run",
         "--evidence-dir", str(evidence)],
        cwd=REPO_ROOT, text=True, capture_output=True, check=False,
    )

    assert completed.returncode == 0, completed.stderr
    receipt = json.loads((evidence / "receipt.json").read_text(encoding="utf-8"))
    challenge = receipt["input"]["challenge"]
    assert receipt["classification"] == "OFFLINE_PROVIDER_GATE_DRY_RUN"
    assert receipt["status"] == "PASS"
    assert receipt["requestBudget"]["adapterRequests"] == 1
    assert receipt["requestBudget"]["externalRequests"] == 0
    assert receipt["requestBudget"]["automaticRetriesAllowed"] is False
    assert challenge not in receipt["result"]["prompt"]
    assert challenge in receipt["result"]["rawOutput"]
    assert len(receipt["input"]["frames"]) == 3
    assert all((evidence / "frames" / item["file"]).is_file()
               for item in receipt["input"]["frames"])


def test_live_mode_rejects_missing_confirmation_before_request(tmp_path):
    completed = subprocess.run(
        [sys.executable, str(SCRIPT), "--mode", "live",
         "--evidence-dir", str(tmp_path / "evidence")],
        cwd=REPO_ROOT, text=True, capture_output=True, check=False,
    )

    assert completed.returncode == 2
    output = json.loads(completed.stdout)
    assert output["status"] == "BLOCKED"
    assert "confirmation" in output["error"]
    assert not (tmp_path / "evidence").exists()


def test_provider_env_requires_0600_and_never_echoes_value(tmp_path):
    gate = _load_gate_module()
    provider_file = tmp_path / "worker-provider.env"
    secret = "test-secret-that-must-not-appear"
    provider_file.write_text(
        f"FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY={secret}\n",
        encoding="utf-8",
    )
    provider_file.chmod(0o644)

    with pytest.raises(gate.GateError) as exc_info:
        gate.load_provider_key(
            provider_file, "FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY")

    assert secret not in str(exc_info.value)
    provider_file.chmod(0o600)
    assert gate.load_provider_key(
        provider_file, "FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY") == secret


def test_one_shot_transport_rejects_second_request():
    gate = _load_gate_module()
    transport = gate.OneShotPost(lambda *args, **kwargs: gate.DryRunResponse("FF-TEST-CODE"),
                                 network=False)

    transport("https://example.invalid")
    with pytest.raises(gate.GateError, match="budget exhausted"):
        transport("https://example.invalid")


def test_simulated_live_receipt_redacts_secret_route_and_env_name(tmp_path):
    gate = _load_gate_module()
    secret = "simulated-live-secret-7Jm9vQ2x"
    provider_file = tmp_path / "worker-provider.env"
    provider_file.write_text(
        f"FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY={secret}\n",
        encoding="utf-8",
    )
    provider_file.chmod(0o600)
    evidence = tmp_path / "live-evidence"
    args = gate.parse_args([
        "--mode", "live",
        "--provider-env-file", str(provider_file),
        "--evidence-dir", str(evidence),
        "--confirm", gate.LIVE_CONFIRMATION,
        "--ack-exposed-key-risk",
    ])

    class EchoResponse(gate.DryRunResponse):
        def __init__(self):
            self._payload = {
                "output": [{"content": [{
                    "type": "output_text",
                    "text": (
                        '{"dimension":"visual_challenge","verdict":"UNKNOWN",'
                        f'"reason":"{secret} {gate.EXPECTED_BASE_URL} '
                        'FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY"}'
                    ),
                }]}],
                "usage": {"total_tokens": 1},
            }

    assert gate.execute(
        args, live_delegate=lambda *a, **k: EchoResponse(),
        enforce_live_repo=False,
    ) == 1

    rendered = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (evidence / "receipt.json", evidence / "report.md")
    )
    assert secret not in rendered
    assert gate.EXPECTED_BASE_URL not in rendered
    assert "FRAMEFLOW_MODEL_OPENCODE_GO_LUNA_V1_API_KEY" not in rendered
    assert "[REDACTED]" in rendered
