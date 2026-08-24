#!/usr/bin/env python3
"""Execute one controlled multimodal Provider request with synthetic frames.

Live mode is deliberately narrow: one fixed OpenCode Go model and endpoint, one
adapter call, no retries, three generated JPEG frames, and a new evidence root.
The API key is read from a 0600 env file and is never accepted on the command
line, copied into the process environment, printed, or written to evidence.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import subprocess
import sys
import time
from datetime import datetime, timezone
from typing import Any, Callable

import cv2
import numpy as np
import requests


REPO_ROOT = Path(__file__).resolve().parents[1]
WORKER_SRC = REPO_ROOT / "frameflow-ai-worker" / "src"
sys.path.insert(0, str(WORKER_SRC))

from frameflow_ai.model_catalog import parse_model_catalog  # noqa: E402
from frameflow_ai.providers import (  # noqa: E402
    OpenAIResponsesProvider,
    ProviderError,
    SemanticRequest,
)


EXPECTED_MODEL_ID = "opencode-go-luna-v1"
EXPECTED_MODEL = "gpt-5.6-luna"
EXPECTED_PROVIDER = "openai-responses"
EXPECTED_BASE_URL = "https://opencode.ai/zen/go/v1"
LIVE_CONFIRMATION = "LIVE_PROVIDER_SYNTHETIC_ONE_REQUEST"
SAFE_TOKEN = re.compile(r"^[A-Za-z0-9._~+/=:-]{16,}$")
CHALLENGE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
DIMENSIONS = ["visual_challenge", "frame_sequence"]
AUTHORIZED_BRANCH = "frameflow-select/learning-main"


class GateError(Exception):
    """A controlled failure whose message never contains Provider secrets."""


class OneShotPost:
    """Wrap an HTTP transport and reject every call after the first one."""

    def __init__(self, delegate: Callable[..., requests.Response], *, network: bool):
        self._delegate = delegate
        self._network = network
        self._response: requests.Response | Any | None = None
        self.calls = 0
        self.duration_ms: int | None = None

    def __call__(self, *args: Any, **kwargs: Any) -> requests.Response:
        if self.calls != 0:
            raise GateError("Provider request budget exhausted")
        self.calls = 1
        started = time.monotonic()
        try:
            self._response = self._delegate(*args, **kwargs)
            return self._response
        finally:
            self.duration_ms = round((time.monotonic() - started) * 1000)

    def safe_metadata(self) -> dict[str, Any]:
        response = self._response
        metadata: dict[str, Any] = {
            "adapterRequests": self.calls,
            "externalRequests": self.calls if self._network else 0,
            "requestDurationMs": self.duration_ms,
        }
        if response is not None:
            status_code = getattr(response, "status_code", None)
            if isinstance(status_code, int):
                metadata["httpStatus"] = status_code
            headers = getattr(response, "headers", {}) or {}
            request_id = headers.get("x-request-id") or headers.get("request-id")
            if isinstance(request_id, str) and re.fullmatch(r"[A-Za-z0-9._:-]{1,160}", request_id):
                metadata["providerRequestId"] = request_id
            try:
                payload = response.json()
            except (TypeError, ValueError, requests.RequestException):
                payload = None
            if isinstance(payload, dict) and isinstance(payload.get("usage"), dict):
                metadata["usage"] = _numeric_usage(payload["usage"])

            # requests.Response retains PreparedRequest headers, including Authorization.
            # Remove it before retaining or inspecting any response metadata further.
            prepared = getattr(response, "request", None)
            prepared_headers = getattr(prepared, "headers", None)
            if prepared_headers is not None:
                prepared_headers.pop("Authorization", None)
        self._response = None
        return metadata


class DryRunResponse:
    status_code = 200
    headers = {"x-request-id": "offline-dry-run"}
    request = None

    def __init__(self, challenge: str):
        self._payload = {
            "output": [{
                "type": "message",
                "content": [{
                    "type": "output_text",
                    "text": (
                        '{"dimension":"visual_challenge","verdict":"PASS",'
                        f'"reason":"读取到挑战码 {challenge}"}}\n'
                        '{"dimension":"frame_sequence","verdict":"PASS",'
                        '"reason":"三个合成帧编号连续，橙色三角形从左向右移动"}'
                    ),
                }],
            }],
            "usage": {"input_tokens": 101, "output_tokens": 37, "total_tokens": 138},
        }

    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict[str, Any]:
        return self._payload


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _numeric_usage(value: dict[str, Any]) -> dict[str, int | float]:
    """Allow only flat numeric accounting fields from an untrusted response."""
    result: dict[str, int | float] = {}
    for key, item in value.items():
        if (isinstance(key, str) and re.fullmatch(r"[A-Za-z0-9_]{1,80}", key)
                and isinstance(item, (int, float)) and not isinstance(item, bool)):
            result[key] = item
    return result


def load_fixed_catalog(path: Path):
    if not path.is_file() or path.is_symlink():
        raise GateError("catalog must be a regular non-symlink file")
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
        selected = parse_model_catalog(parsed).select()
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        raise GateError("catalog failed the strict model contract") from None
    if (selected.id != EXPECTED_MODEL_ID
            or selected.model != EXPECTED_MODEL
            or selected.provider != EXPECTED_PROVIDER
            or selected.base_url != EXPECTED_BASE_URL):
        raise GateError("catalog is not the authorized OpenCode Go Luna route")
    return selected


def git_source() -> dict[str, Any]:
    def run(*arguments: str) -> str:
        completed = subprocess.run(
            ["git", *arguments], cwd=REPO_ROOT, text=True,
            capture_output=True, check=False,
        )
        if completed.returncode != 0:
            raise GateError("Git source identity could not be resolved")
        return completed.stdout.strip()

    return {
        "gitCommit": run("rev-parse", "HEAD"),
        "branch": run("branch", "--show-current"),
        "worktreeCleanAtStart": not bool(run("status", "--porcelain=v1")),
    }


def load_provider_key(path: Path, expected_name: str) -> str:
    if not path.is_absolute():
        raise GateError("Provider env file must use an absolute path")
    try:
        metadata = path.lstat()
    except FileNotFoundError:
        raise GateError("Provider env file is absent") from None
    if (stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode)
            or stat.S_IMODE(metadata.st_mode) != 0o600
            or metadata.st_uid != os.geteuid()):
        raise GateError("Provider env file must be owned by this user, regular, non-symlink, and 0600")

    values: dict[str, str] = {}
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError):
        raise GateError("Provider env file could not be read safely") from None
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line or line.startswith("export "):
            raise GateError("Provider env file has an invalid data-only format")
        name, value = line.split("=", 1)
        if not re.fullmatch(r"[A-Z_][A-Z0-9_]*", name) or name in values:
            raise GateError("Provider env file has an invalid or duplicate variable")
        values[name] = value.strip()
    if set(values) != {expected_name}:
        raise GateError("Provider env file key set does not match the authorized model")
    key = values[expected_name]
    if not SAFE_TOKEN.fullmatch(key):
        raise GateError("Provider credential does not match the safe token contract")
    return key


def generate_frames(directory: Path, challenge: str) -> tuple[list[bytes], list[dict[str, Any]]]:
    directory.mkdir(parents=True, exist_ok=False)
    frames: list[bytes] = []
    descriptors: list[dict[str, Any]] = []
    for index in range(1, 4):
        image = np.full((720, 1280, 3), (126, 58, 18), dtype=np.uint8)
        cv2.rectangle(image, (60, 55), (1220, 665), (245, 245, 245), -1)
        cv2.rectangle(image, (60, 55), (1220, 665), (255, 178, 40), 8)
        x = 180 + (index - 1) * 350
        triangle = np.array([[x, 430], [x + 105, 250], [x + 210, 430]], dtype=np.int32)
        cv2.fillPoly(image, [triangle], (0, 132, 255))
        cv2.putText(image, "SYNTHETIC ONLY", (360, 135), cv2.FONT_HERSHEY_SIMPLEX,
                    1.35, (25, 25, 25), 3, cv2.LINE_AA)
        cv2.putText(image, challenge, (215, 575), cv2.FONT_HERSHEY_SIMPLEX,
                    2.15, (15, 15, 15), 5, cv2.LINE_AA)
        cv2.putText(image, f"FRAME {index}/3", (850, 625), cv2.FONT_HERSHEY_SIMPLEX,
                    1.1, (25, 25, 25), 3, cv2.LINE_AA)
        ok, encoded = cv2.imencode(".jpg", image, [cv2.IMWRITE_JPEG_QUALITY, 92])
        if not ok:
            raise GateError("synthetic JPEG encoding failed")
        data = encoded.tobytes()
        output = directory / f"frame-{index:02d}.jpg"
        output.write_bytes(data)
        frames.append(data)
        descriptors.append({
            "file": output.name,
            "byteLength": len(data),
            "sha256": sha256(data),
            "timecodeMs": (index - 1) * 1000,
        })
    return frames, descriptors


def build_visual_brief() -> str:
    # ★ 核心：挑战码只存在于图片，不进入 prompt。模型必须真正读取视觉输入，
    # 不能仅凭文字要求猜一个 PASS。
    return (
        "这是完全由程序生成的三帧测试图，不含人物、品牌、客户或个人数据。"
        "画面中央包含一个一次性视觉挑战码，但本段文字不会提供该码。"
        "对 visual_challenge：只有实际读取完整挑战码时才能 PASS，reason 必须逐字写出该码；"
        "否则回答 UNKNOWN。对 frame_sequence：只有确认三帧依次标为 1/3、2/3、3/3，"
        "且橙色三角形从左向右移动时才能 PASS；否则回答 UNKNOWN。"
    )


def _prepare_evidence_root(path: Path) -> None:
    if not path.is_absolute():
        raise GateError("evidence directory must use an absolute path")
    if path.exists() or path.is_symlink():
        raise GateError("evidence directory must not already exist")
    parent = path.parent
    if not parent.is_dir() or parent.is_symlink():
        raise GateError("evidence parent must be an existing non-symlink directory")
    path.mkdir(mode=0o755)


def _scrub(value: Any, forbidden: tuple[str, ...]) -> Any:
    if isinstance(value, str):
        redacted = value
        for item in forbidden:
            if item:
                redacted = redacted.replace(item, "[REDACTED]")
        return redacted
    if isinstance(value, dict):
        return {key: _scrub(item, forbidden) for key, item in value.items()}
    if isinstance(value, list):
        return [_scrub(item, forbidden) for item in value]
    return value


def _write_evidence(root: Path, receipt: dict[str, Any], forbidden: tuple[str, ...]) -> None:
    safe_receipt = _scrub(receipt, forbidden)
    receipt_path = root / "receipt.json"
    receipt_path.write_text(
        json.dumps(safe_receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    result = safe_receipt.get("result", {})
    lines = [
        "# F6 REAL_PROVIDER_SYNTHETIC_GATE",
        "",
        f"- 状态：`{safe_receipt['status']}`",
        f"- 模型：`{safe_receipt['provider']['model']}`",
        f"- 外部请求：`{safe_receipt['requestBudget']['externalRequests']}` / 1",
        "- 数据：程序生成的 3 张 JPEG；真实客户媒体 0",
        f"- 视觉挑战匹配：`{result.get('visualChallengeMatched', False)}`",
        f"- 开始：`{safe_receipt['startedAt']}`",
        f"- 结束：`{safe_receipt['finishedAt']}`",
        "",
        "> 该证据只验证一次真实供应商协议和合成视觉输入，不代表生产、真实试点或产品价值证明。",
        "",
    ]
    (root / "report.md").write_text("\n".join(lines), encoding="utf-8")

    files = sorted(path for path in root.rglob("*") if path.is_file())
    manifest = "".join(
        f"{sha256(path.read_bytes())}  {path.relative_to(root).as_posix()}\n"
        for path in files
    )
    (root / "artifact-checksums.sha256").write_text(manifest, encoding="utf-8")


def execute(args: argparse.Namespace, *,
            live_delegate: Callable[..., requests.Response] | None = None,
            enforce_live_repo: bool = True) -> int:
    selected = load_fixed_catalog(args.catalog)
    source = git_source()
    if args.mode == "live":
        if args.confirm != LIVE_CONFIRMATION:
            raise GateError("live mode requires the exact one-request confirmation")
        if not args.ack_exposed_key_risk:
            raise GateError("owner acknowledgement is required for reuse of the exposed credential")
        if enforce_live_repo and (source["branch"] != AUTHORIZED_BRANCH
                                  or not source["worktreeCleanAtStart"]):
            raise GateError("live mode requires the clean authorized learning branch")
        if args.provider_env_file is None:
            raise GateError("live mode requires a Provider env file")
        api_key = load_provider_key(args.provider_env_file, selected.api_key_env)
    else:
        api_key = "offline-dry-run-token-7Jm9vQ2x"

    _prepare_evidence_root(args.evidence_dir)
    challenge = "FF-" + "".join(secrets.choice(CHALLENGE_ALPHABET) for _ in range(4)) \
        + "-" + "".join(secrets.choice(CHALLENGE_ALPHABET) for _ in range(4))
    started = utc_now()
    started_clock = time.monotonic()
    frames, frame_descriptors = generate_frames(args.evidence_dir / "frames", challenge)
    brief = build_visual_brief()

    if args.mode == "live":
        if live_delegate is None:
            session = requests.Session()
            session.trust_env = False
            live_delegate = session.post
        transport = OneShotPost(live_delegate, network=True)
    else:
        transport = OneShotPost(lambda *a, **k: DryRunResponse(challenge), network=False)

    receipt: dict[str, Any] = {
        "schemaVersion": "frameflow.real-provider-synthetic-gate.v1",
        "classification": (
            "REAL_PROVIDER_SYNTHETIC_GATE" if args.mode == "live"
            else "OFFLINE_PROVIDER_GATE_DRY_RUN"
        ),
        "status": "RUNNING",
        "startedAt": started,
        "source": source,
        "provider": {
            "logicalModelId": selected.id,
            "model": selected.model,
            "protocol": selected.provider,
        },
        "authorization": {
            "ownerAuthorized": args.mode == "live",
            "exposedCredentialRotationDeclined": (
                bool(args.ack_exposed_key_risk) if args.mode == "live" else False
            ),
        },
        "dataBoundary": {
            "source": "PROGRAM_GENERATED",
            "syntheticOnly": True,
            "customerMediaCount": 0,
            "jpegFrameCount": len(frames),
        },
        "requestBudget": {
            "maximumExternalRequests": 1 if args.mode == "live" else 0,
            "automaticRetriesAllowed": False,
            "adapterRequests": 0,
            "externalRequests": 0,
        },
        "input": {
            "challenge": challenge,
            "challengeAbsentFromPrompt": challenge not in brief,
            "frames": frame_descriptors,
        },
        "checks": [],
    }

    provider = OpenAIResponsesProvider(
        base_url=selected.base_url,
        api_key=api_key,
        model=selected.model,
        model_id=selected.id,
        timeout_s=args.timeout,
        post=transport,
    )
    forbidden = (api_key, selected.base_url, selected.api_key_env)
    try:
        semantic_request = SemanticRequest(
            brief_content=brief,
            frames_jpeg=frames,
            frame_timecodes_ms=[0, 1000, 2000],
            dimensions=DIMENSIONS,
            candidate_hint="synthetic-live-provider-gate",
        )
        result = provider.analyze(semantic_request)
        transport_metadata = transport.safe_metadata()
        verdicts = {
            verdict.dimension: {"verdict": verdict.verdict, "reason": verdict.reason}
            for verdict in result.verdicts
        }
        visual_reason = verdicts.get("visual_challenge", {}).get("reason", "")
        checks = [
            {"name": "request_budget_exactly_one", "status": (
                "PASS" if transport.calls == 1 else "FAIL")},
            {"name": "challenge_absent_from_prompt", "status": (
                "PASS" if challenge not in result.prompt_snapshot else "FAIL")},
            {"name": "visual_challenge_pass", "status": (
                "PASS" if verdicts.get("visual_challenge", {}).get("verdict") == "PASS" else "FAIL")},
            {"name": "visual_challenge_exact_match", "status": (
                "PASS" if challenge.lower() in visual_reason.lower() else "FAIL")},
            {"name": "frame_sequence_pass", "status": (
                "PASS" if verdicts.get("frame_sequence", {}).get("verdict") == "PASS" else "FAIL")},
        ]
        passed = all(check["status"] == "PASS" for check in checks)
        receipt["checks"] = checks
        receipt["requestBudget"].update(transport_metadata)
        receipt["result"] = {
            "provider": result.provider,
            "providerVersion": result.provider_version,
            "verdicts": verdicts,
            "prompt": result.prompt_snapshot,
            "rawOutput": result.raw_output[:16000],
            "visualChallengeMatched": challenge.lower() in visual_reason.lower(),
        }
        receipt["status"] = "PASS" if passed else "FAIL"
    except ProviderError as exc:
        receipt["requestBudget"].update(transport.safe_metadata())
        receipt["status"] = "FAIL"
        receipt["error"] = {"type": "PROVIDER_ERROR", "message": str(exc)}
    except Exception as exc:  # noqa: BLE001 - final redacted receipt boundary
        receipt["requestBudget"].update(transport.safe_metadata())
        receipt["status"] = "FAIL"
        receipt["error"] = {"type": "GATE_ERROR", "message": str(exc)}
    finally:
        provider.api_key = ""
        api_key_for_scrub = api_key
        api_key = ""

    receipt["finishedAt"] = utc_now()
    receipt["durationMs"] = round((time.monotonic() - started_clock) * 1000)
    _write_evidence(
        args.evidence_dir,
        receipt,
        (api_key_for_scrub, selected.base_url, selected.api_key_env),
    )
    print(json.dumps({
        "status": receipt["status"],
        "classification": receipt["classification"],
        "externalRequests": receipt["requestBudget"]["externalRequests"],
        "evidence": str(args.evidence_dir / "receipt.json"),
    }, ensure_ascii=False))
    return 0 if receipt["status"] == "PASS" else 1


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("dry-run", "live"), required=True)
    parser.add_argument(
        "--catalog", type=Path,
        default=REPO_ROOT / "experiments" / "fixtures" / "pre-f9-correctness"
        / "model-catalog.opencode-go-luna.example.json",
    )
    parser.add_argument("--provider-env-file", type=Path)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--timeout", type=float, default=60.0)
    parser.add_argument("--confirm", default="")
    parser.add_argument("--ack-exposed-key-risk", action="store_true")
    args = parser.parse_args(argv)
    if not 1.0 <= args.timeout <= 120.0:
        parser.error("--timeout must be between 1 and 120 seconds")
    # 不 resolve Secret 路径：resolve 会先跟随 symlink，使后面的 lstat 防护失效。
    # CLI 明确要求 evidence/Secret 使用绝对路径，调用方必须自己选定精确目标。
    return args


def main(argv: list[str] | None = None) -> int:
    try:
        return execute(parse_args(argv))
    except GateError as exc:
        print(json.dumps({"status": "BLOCKED", "error": str(exc)}, ensure_ascii=False))
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
