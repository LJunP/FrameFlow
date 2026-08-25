#!/usr/bin/env python3
"""Run the single-request synthetic full-product real Provider E2E gate.

The script talks only to the local FrameFlow API/Web. The one external
``POST /responses`` is made by the normal RabbitMQ Worker after the synthetic
candidate is dispatched. stdout contains exactly one redacted JSON receipt.
"""

from __future__ import annotations

import argparse
import csv
import getpass
import io
import json
import os
from pathlib import Path
import re
import sys
import time
from typing import Any
import urllib.error
import urllib.request
import uuid


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import run_pre_f9_api_gate as common  # noqa: E402


REPO_ROOT = SCRIPT_DIR.parent
DEFAULT_API_BASE = "http://127.0.0.1:18081/api/v1"
DEFAULT_WEB_BASE = "http://127.0.0.1:3001"
DEFAULT_EMAIL = "frameflow-real-provider-e2e@example.test"
MODEL_ID = "opencode-go-luna-v1"
MODEL_NAME = "gpt-5.6-luna"
PROVIDER_NAME = "semantic-openai-responses"
SEMANTIC_DIMENSIONS = (
    "prompt_alignment",
    "quality_impression",
    "policy_violation",
)
CHALLENGE_PATTERN = re.compile(r"FF-E2E-[A-Z0-9]{4}-[A-Z0-9]{4}")


class E2EReceipt(common.Receipt):
    """Receipt specialized for one synthetic candidate and one Provider call."""

    def __init__(self, api_base: str, web_base: str) -> None:
        super().__init__(api_base)
        self.data = {
            "schemaVersion": "frameflow.real-provider-full-e2e.v1",
            "classification": "REAL_PROVIDER_FULL_PRODUCT_E2E_SYNTHETIC_ONLY",
            "status": "RUNNING",
            "startedAt": common.utc_now(),
            "apiBase": common.safe_url(api_base),
            "webBase": common.safe_url(web_base),
            "providerPolicy": {
                "provider": PROVIDER_NAME,
                "modelId": MODEL_ID,
                "model": MODEL_NAME,
                "endpointPath": "/responses",
                "maximumExternalRequests": 1,
                "automaticRetryAllowed": False,
                "customerDataAllowed": False,
            },
            "checks": [],
            "fixtures": {},
            "resources": {},
            "analysis": {},
            "ranking": {},
            "selection": {},
            "exports": {},
        }


def build_brief() -> str:
    """The visual code is intentionally absent: only an image-capable model can read it."""
    return (
        "这是 FrameFlow Select 的合成媒体门禁，不包含真人、客户、品牌或生产数据。\n"
        "prompt_alignment：读取关键帧中 VISUAL CHALLENGE 后的完整代码，"
        "必须把代码原样写入 reason；读不清则 UNKNOWN。\n"
        "quality_impression：检查三张关键帧的 PHASE 是否按 1/3、2/3、3/3 前进，"
        "且亮色三角形是否从画面左侧移动到右侧；满足则 PASS。\n"
        "policy_violation：画面只能含合成图形与合成文字，不得含真人、客户资料、"
        "真实品牌或危险内容；确认无上述内容则 PASS。"
    )


def build_profile() -> dict[str, Any]:
    return {
        "dimensions": {
            "duration": {"min": 2, "max": 10, "severity": "BLOCKER"},
            "resolution": {"minWidth": 320, "minHeight": 180, "severity": "BLOCKER"},
            "fps": {"min": 20, "severity": "BLOCKER"},
        },
        "weights": {
            "duration": 10,
            "resolution": 10,
            "fps": 10,
            "prompt_alignment": 12,
            "quality_impression": 12,
            "policy_violation": 16,
        },
        "semantic": {
            "enabled": True,
            "modelId": MODEL_ID,
            "dimensions": list(SEMANTIC_DIMENSIONS),
        },
        "duplicates": {"hammingThreshold": 6},
    }


def generate_synthetic_video(path: Path, challenge: str) -> dict[str, Any]:
    """Generate a six-second visual-only MP4 whose sampled frames carry the challenge."""
    if CHALLENGE_PATTERN.fullmatch(challenge) is None:
        raise common.ContractError(
            "--challenge must match FF-E2E-XXXX-XXXX using uppercase letters/digits")
    if path.is_symlink():
        raise common.ContractError("synthetic media output must not be a symlink")
    path.parent.mkdir(parents=True, exist_ok=True)
    try:
        import cv2
        import numpy as np
    except ImportError as exc:
        raise common.ContractError("OpenCV and numpy are required to generate synthetic media") from exc

    width, height, fps, seconds = 1280, 720, 24, 6
    frame_count = fps * seconds
    writer = cv2.VideoWriter(
        str(path), cv2.VideoWriter_fourcc(*"mp4v"), float(fps), (width, height))
    if not writer.isOpened():
        raise common.ContractError("OpenCV could not open the synthetic MP4 writer")
    try:
        for index in range(frame_count):
            progress = index / max(1, frame_count - 1)
            phase = min(3, index // (frame_count // 3) + 1)
            frame = np.zeros((height, width, 3), dtype=np.uint8)
            frame[:, :] = (48 + index % 30, 70 + (index * 2) % 40, 96)
            stripe_x = int((index * 17) % width)
            cv2.rectangle(frame, (stripe_x, 0), (min(width - 1, stripe_x + 80), height),
                          (80, 118, 150), -1)
            cv2.putText(frame, "SYNTHETIC PROVIDER E2E", (84, 105),
                        cv2.FONT_HERSHEY_SIMPLEX, 1.6, (255, 255, 255), 4, cv2.LINE_AA)
            cv2.putText(frame, "VISUAL CHALLENGE", (84, 235),
                        cv2.FONT_HERSHEY_SIMPLEX, 1.25, (225, 235, 245), 3, cv2.LINE_AA)
            cv2.putText(frame, challenge, (84, 335),
                        cv2.FONT_HERSHEY_SIMPLEX, 2.25, (255, 255, 255), 6, cv2.LINE_AA)
            cv2.putText(frame, f"PHASE {phase}/3", (84, 445),
                        cv2.FONT_HERSHEY_SIMPLEX, 1.6, (240, 245, 250), 4, cv2.LINE_AA)
            center_x = int(140 + progress * 1000)
            triangle = np.array([
                [center_x, 515], [center_x - 66, 635], [center_x + 66, 635]
            ], dtype=np.int32)
            cv2.fillConvexPoly(frame, triangle, (235, 242, 250), cv2.LINE_AA)
            cv2.putText(frame, f"FRAME {index + 1:03d}/{frame_count}", (870, 690),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.75, (235, 240, 245), 2, cv2.LINE_AA)
            writer.write(frame)
    finally:
        writer.release()
    if not path.is_file() or path.stat().st_size <= 0:
        raise common.ContractError("synthetic MP4 was not created")
    return {
        "path": str(path.relative_to(REPO_ROOT)) if path.is_relative_to(REPO_ROOT) else path.name,
        "contentType": "video/mp4",
        "sizeBytes": path.stat().st_size,
        "sha256": common.sha256_file(path),
        "width": width,
        "height": height,
        "fps": fps,
        "durationSeconds": seconds,
        "frameCount": frame_count,
        "syntheticOnly": True,
        "hasCustomerData": False,
        "visualChallenge": challenge,
    }


def web_smoke(web_base: str, timeout: float, receipt: E2EReceipt) -> None:
    request = urllib.request.Request(web_base.rstrip("/") + "/", method="GET")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=timeout) as response:
            body = response.read(1024 * 1024)
            status = int(response.status)
    except (urllib.error.URLError, TimeoutError, OSError) as exc:
        raise common.ApiError("local Web smoke failed") from exc
    receipt.require("web.root.http_200", status == 200)
    receipt.require("web.root.non_empty_html", b"<html" in body.lower())


def create_resources(client: common.ApiClient, receipt: E2EReceipt,
                     run_suffix: str) -> int:
    project = common.expect_dict(client.json(
        "POST", "/projects", expected={201}, body={
            "name": f"Real Provider E2E {run_suffix}",
            "description": "Synthetic-only full product E2E; not customer pilot evidence.",
        }), "project")
    project_id = common.integer_field(project, "id", "project")
    brief = build_brief()
    brief_response = common.expect_dict(client.json(
        "POST", f"/projects/{project_id}/briefs", expected={201},
        body={"content": brief}), "brief")
    brief_id = common.integer_field(brief_response, "id", "brief")
    profile = build_profile()
    profile_response = common.expect_dict(client.json(
        "POST", "/quality-profiles", expected={201}, body={
            "name": f"OpenCode Luna E2E {run_suffix}",
            "description": "One-call synthetic visual challenge gate.",
            "spec": json.dumps(profile, ensure_ascii=False, sort_keys=True,
                               separators=(",", ":")),
        }), "quality profile")
    profile_id = common.integer_field(profile_response, "id", "quality profile")
    profile_version = common.expect_dict(client.json(
        "GET", f"/quality-profiles/{profile_id}/versions/1", expected={200}),
        "quality profile version")
    batch = common.expect_dict(client.json(
        "POST", "/batches", expected={201}, body={
            "projectId": project_id, "profileId": profile_id, "capacity": 1,
        }), "batch")
    batch_id = common.integer_field(batch, "id", "batch")
    version_spec = common.expect_dict(profile_version.get("spec"), "quality profile version.spec")
    receipt.require("profile.model_snapshot",
                    version_spec.get("semantic", {}).get("modelId") == MODEL_ID)
    receipt.require("batch.brief_snapshot_binding", batch.get("briefId") == brief_id)
    receipt.require("batch.profile_version", batch.get("profileVersionNo") == 1)
    receipt.require("batch.initial_status", batch.get("status") == "OPEN")
    receipt.data["resources"] = {
        "projectId": project_id,
        "briefId": brief_id,
        "profileId": profile_id,
        "profileVersionId": batch.get("profileVersionId"),
        "batchId": batch_id,
    }
    return batch_id


def upload_candidate(client: common.ApiClient, receipt: E2EReceipt,
                     batch_id: int, media_path: Path) -> int:
    registered = common.expect_dict(client.json(
        "POST", f"/batches/{batch_id}/candidates", expected={201}, body={
            "fileName": "synthetic-visual-challenge.mp4",
            "contentType": "video/mp4",
            "sizeBytes": media_path.stat().st_size,
        }), "candidate registration")
    candidate_id = common.integer_field(registered, "candidateId", "candidate")
    receipt.require("minio.presigned_upload.simple_mode", registered.get("mode") == "SIMPLE")
    upload_url = common.string_field(registered, "uploadUrl", "candidate")
    client.put_file(upload_url, media_path, "video/mp4")
    completed = common.expect_dict(client.json(
        "POST", f"/candidates/{candidate_id}/complete", expected={200}, body={}),
        "candidate complete")
    receipt.require("minio.upload.completed", completed.get("status") == "UPLOADED")
    receipt.require("minio.upload.candidate_binding", completed.get("candidateId") == candidate_id)
    receipt.data["resources"]["candidateId"] = candidate_id
    return candidate_id


def dispatch_and_wait(client: common.ApiClient, receipt: E2EReceipt,
                      batch_id: int, candidate_id: int, timeout: float,
                      poll_interval: float) -> dict[str, Any]:
    closed = common.expect_dict(client.json(
        "POST", f"/batches/{batch_id}/close", expected={200}, body={}), "close batch")
    receipt.require("batch.closed", closed.get("status") == "CLOSED")
    dispatched = common.expect_dict(client.json(
        "POST", f"/batches/{batch_id}/analyze", expected={202}, body={}), "analyze batch")
    receipt.require("rabbitmq.single_task_dispatched", dispatched.get("dispatched") == 1)
    receipt.require("rabbitmq.no_task_skipped", dispatched.get("skipped") == 0)

    deadline = time.monotonic() + timeout
    last_status = None
    candidate: dict[str, Any] = {}
    while time.monotonic() < deadline:
        page = common.expect_dict(client.json(
            "GET", f"/batches/{batch_id}/candidates", expected={200},
            query={"page": 0, "size": 10}), "candidate page")
        items = common.expect_list(page.get("items"), "candidate page.items")
        matches = [common.expect_dict(item, "candidate") for item in items
                   if isinstance(item, dict) and item.get("id") == candidate_id]
        if len(matches) != 1:
            raise common.ContractError("candidate page lost the single registered candidate")
        candidate = matches[0]
        status = candidate.get("status")
        if status != last_status:
            common.eprint(f"full E2E analysis progress: candidate={candidate_id} status={status}")
            last_status = status
        if status in common.TERMINAL_CANDIDATE_STATUSES:
            break
        time.sleep(poll_interval)
    else:
        raise common.GateError(f"analysis did not finish within {timeout:g} seconds")
    receipt.require("java_callback.terminal_status", candidate.get("status") == "ANALYZED",
                    {"actual": candidate.get("status"), "expected": "ANALYZED"})
    receipt.data["analysis"]["candidateStatus"] = candidate.get("status")
    return candidate


def _parse_finding_evidence(finding: dict[str, Any], label: str) -> dict[str, Any]:
    raw = finding.get("evidence")
    if not isinstance(raw, str):
        raise common.ContractError(f"{label}.evidence must be a JSON string")
    try:
        return common.expect_dict(json.loads(raw), f"{label}.evidence")
    except json.JSONDecodeError as exc:
        raise common.ContractError(f"{label}.evidence is invalid JSON") from exc


def validate_findings(client: common.ApiClient, receipt: E2EReceipt,
                      candidate_id: int, challenge: str) -> None:
    raw = common.expect_list(client.json(
        "GET", f"/candidates/{candidate_id}/findings", expected={200}), "findings")
    findings = [common.expect_dict(item, f"findings[{index}]")
                for index, item in enumerate(raw)]
    deterministic = {item.get("dimension"): item for item in findings
                     if item.get("dimension") in {"duration", "resolution", "fps",
                                                  "black_frame", "freeze"}}
    receipt.require("deterministic.all_pass", set(deterministic) == {
        "duration", "resolution", "fps", "black_frame", "freeze"
    } and all(item.get("passed") is True for item in deterministic.values()))

    semantic = {item.get("dimension"): item for item in findings
                if item.get("detector") == "semantic"}
    receipt.require("provider.semantic_dimensions_exact",
                    set(semantic) == set(SEMANTIC_DIMENSIONS)
                    and len([item for item in findings if item.get("detector") == "semantic"]) == 3)
    evidence_by_dimension: dict[str, dict[str, Any]] = {}
    for dimension in SEMANTIC_DIMENSIONS:
        finding = semantic[dimension]
        evidence = _parse_finding_evidence(finding, f"semantic.{dimension}")
        evidence_by_dimension[dimension] = evidence
        receipt.require(f"provider.{dimension}.pass",
                        finding.get("verdict") == "PASS" and finding.get("passed") is True)
        receipt.require(f"provider.{dimension}.identity",
                        evidence.get("provider") == PROVIDER_NAME
                        and evidence.get("modelId") == MODEL_ID
                        and evidence.get("model") == MODEL_NAME)
        receipt.require(f"provider.{dimension}.single_request_budget",
                        evidence.get("providerRequestOrdinal") == 1
                        and evidence.get("providerRequestBudget") == 1)

    prompt_evidence = evidence_by_dimension["prompt_alignment"]
    prompt = str(prompt_evidence.get("prompt", ""))
    raw_output = str(prompt_evidence.get("rawOutput", ""))
    reason = str(prompt_evidence.get("reason", ""))
    receipt.require("multimodal.challenge_absent_from_prompt", challenge not in prompt)
    receipt.require("multimodal.challenge_read_from_frames",
                    challenge in raw_output and challenge in reason)
    keyframe_hashes = prompt_evidence.get("keyframeSha256")
    keyframe_stamps = prompt_evidence.get("keyframeTimecodesMs")
    receipt.require("multimodal.three_keyframes_hashed",
                    isinstance(keyframe_hashes, list) and len(keyframe_hashes) == 3
                    and all(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value)
                            for value in keyframe_hashes))
    receipt.require("multimodal.three_keyframe_timecodes",
                    isinstance(keyframe_stamps, list) and len(keyframe_stamps) == 3
                    and all(isinstance(value, int) for value in keyframe_stamps))
    receipt.data["analysis"].update({
        "findingCount": len(findings),
        "deterministicDimensions": sorted(deterministic),
        "semantic": {
            "provider": PROVIDER_NAME,
            "modelId": MODEL_ID,
            "model": MODEL_NAME,
            "providerRequestOrdinal": 1,
            "providerRequestBudget": 1,
            "verdicts": {dimension: semantic[dimension].get("verdict")
                         for dimension in SEMANTIC_DIMENSIONS},
            "prompt": prompt,
            "rawOutput": raw_output,
            "reasons": {dimension: evidence_by_dimension[dimension].get("reason")
                        for dimension in SEMANTIC_DIMENSIONS},
            "keyframeTimecodesMs": keyframe_stamps,
            "keyframeSha256": keyframe_hashes,
        },
    })


def rank_lock_export(client: common.ApiClient, receipt: E2EReceipt,
                     batch_id: int, candidate_id: int) -> None:
    ranked = common.expect_dict(client.json(
        "POST", f"/batches/{batch_id}/rank", expected={201}, body={}), "rank")
    snapshot_id = common.integer_field(ranked, "snapshotId", "rank")
    receipt.require("ranking.one_representative",
                    ranked.get("ranked") == 1 and ranked.get("clusters") == 1
                    and ranked.get("excluded") == 0)
    latest = common.expect_dict(client.json(
        "GET", f"/batches/{batch_id}/ranking/latest", expected={200}), "latest ranking")
    entries = common.expect_list(latest.get("entries"), "latest ranking.entries")
    receipt.require("ranking.snapshot_binding", latest.get("snapshotId") == snapshot_id)
    receipt.require("ranking.single_rank_one", len(entries) == 1
                    and entries[0].get("candidateId") == candidate_id
                    and entries[0].get("rankNo") == 1
                    and entries[0].get("representative") is True
                    and entries[0].get("excludedReason") is None)
    receipt.data["ranking"] = {
        "snapshotId": snapshot_id,
        "algorithmVersion": latest.get("algorithmVersion"),
        "ranked": 1,
        "clusters": 1,
        "excluded": 0,
        "candidateId": candidate_id,
        "score": entries[0].get("score"),
    }

    created = common.expect_dict(client.json(
        "POST", f"/batches/{batch_id}/selections", expected={201},
        body={"topK": 1, "snapshotId": snapshot_id}), "selection")
    selection_id = common.integer_field(created, "id", "selection")
    detail = common.expect_dict(client.json(
        "GET", f"/selections/{selection_id}", expected={200}), "selection detail")
    items = common.expect_list(detail.get("items"), "selection detail.items")
    receipt.require("selection.machine_pick_created", len(items) == 1
                    and items[0].get("candidateId") == candidate_id
                    and items[0].get("machinePick") is True)
    locked = common.expect_dict(client.json(
        "POST", f"/selections/{selection_id}/lock", expected={200}, body={}), "lock")
    receipt.require("selection.locked", locked.get("status") == "LOCKED"
                    and isinstance(locked.get("lockedAt"), str)
                    and bool(locked.get("lockedAt")))

    json_headers, json_bytes = client.bytes(
        "GET", f"/selections/{selection_id}/export", expected={200}, query={"format": "json"})
    csv_headers, csv_bytes = client.bytes(
        "GET", f"/selections/{selection_id}/export", expected={200}, query={"format": "csv"})
    try:
        json_rows = common.expect_list(json.loads(json_bytes.decode("utf-8")), "JSON export")
        csv_reader = csv.DictReader(io.StringIO(csv_bytes.decode("utf-8-sig"), newline=""))
        csv_rows = list(csv_reader)
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise common.ContractError("selection export is not valid UTF-8/JSON") from exc
    receipt.require("export.single_candidate", len(json_rows) == len(csv_rows) == 1
                    and json_rows[0].get("candidateId") == candidate_id
                    and csv_rows[0].get("candidate_id") == str(candidate_id))
    receipt.require("export.machine_pick_preserved",
                    json_rows[0].get("machinePick") is True
                    and csv_rows[0].get("machine_pick") == "true")
    receipt.require("export.content_types",
                    json_headers.get("content-type", "").lower().startswith("application/json")
                    and csv_headers.get("content-type", "").lower().startswith("text/csv"))
    receipt.data["selection"] = {
        "selectionId": selection_id,
        "status": "LOCKED",
        "topK": 1,
        "candidateId": candidate_id,
        "machinePick": True,
    }
    receipt.data["exports"] = {
        "json": {"rowCount": 1, "sizeBytes": len(json_bytes),
                 "sha256": common.sha256_bytes(json_bytes)},
        "csv": {"rowCount": 1, "sizeBytes": len(csv_bytes),
                "sha256": common.sha256_bytes(csv_bytes)},
    }


def run_gate(args: argparse.Namespace, receipt: E2EReceipt,
             secrets: common.Secrets) -> None:
    challenge = args.challenge.strip()
    media_path = Path(args.media_output).expanduser().resolve(strict=False)
    fixture = generate_synthetic_video(media_path, challenge)
    brief = build_brief()
    receipt.require("fixture.challenge_not_in_brief", challenge not in brief)
    receipt.data["fixtures"] = {"media": fixture, "brief": brief, "profile": build_profile()}

    password = args.password or os.environ.get("FRAMEFLOW_GATE_PASSWORD")
    if password is None:
        if not sys.stdin.isatty():
            raise common.ContractError("local gate password is required")
        password = getpass.getpass("FrameFlow local E2E password (not echoed): ")
    secrets.add(password)
    run_suffix = common.datetime.now(common.timezone.utc).strftime("%Y%m%dT%H%M%SZ") \
        + "-" + uuid.uuid4().hex[:8]
    client = common.ApiClient(args.api_base, args.request_timeout, secrets)

    ping = common.expect_dict(client.json(
        "GET", "/ping", expected={200}, auth=False), "ping")
    receipt.require("api.ping.app", ping.get("app") == "frameflow-select")
    web_smoke(args.web_base, args.request_timeout, receipt)
    common.authenticate(client, receipt, secrets, args.email, password, run_suffix)
    batch_id = create_resources(client, receipt, run_suffix)
    candidate_id = upload_candidate(client, receipt, batch_id, media_path)
    dispatch_and_wait(client, receipt, batch_id, candidate_id,
                      args.analysis_timeout, args.poll_interval)
    validate_findings(client, receipt, candidate_id, challenge)
    rank_lock_export(client, receipt, batch_id, candidate_id)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Run one synthetic full-product E2E through the real Responses Provider.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("--api-base", default=DEFAULT_API_BASE)
    parser.add_argument("--web-base", default=DEFAULT_WEB_BASE)
    parser.add_argument("--email", default=DEFAULT_EMAIL)
    parser.add_argument("--password", default=None,
                        help="prefer FRAMEFLOW_GATE_PASSWORD or the hidden prompt")
    parser.add_argument("--challenge", required=True,
                        help="safe synthetic visual code matching FF-E2E-XXXX-XXXX")
    parser.add_argument("--media-output", required=True,
                        help="path for the generated synthetic MP4")
    parser.add_argument("--request-timeout", type=common.positive_float, default=15.0)
    parser.add_argument("--analysis-timeout", type=common.positive_float, default=240.0)
    parser.add_argument("--poll-interval", type=common.positive_float, default=1.0)
    return parser


def emit_receipt(receipt: E2EReceipt, secrets: common.Secrets) -> None:
    print(json.dumps(secrets.scrub(receipt.data), ensure_ascii=False, sort_keys=True))


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    secrets = common.Secrets()
    secrets.add(args.password)
    try:
        args.api_base = common.normalize_api_base(args.api_base, False)
        args.web_base = common.normalize_api_base(args.web_base, False)
        # normalize_api_base always adds /api/v1; Web needs an origin only.
        args.web_base = args.web_base.removesuffix("/api/v1")
    except common.GateError as exc:
        receipt = E2EReceipt(args.api_base, args.web_base)
        receipt.finish("FAIL", {"type": "CONFIG_ERROR", "message": secrets.redact(str(exc))})
        emit_receipt(receipt, secrets)
        return 2
    receipt = E2EReceipt(args.api_base, args.web_base)
    try:
        run_gate(args, receipt, secrets)
    except common.ApiError as exc:
        error: dict[str, Any] = {"type": "API_ERROR", "message": secrets.redact(str(exc))}
        if exc.status is not None:
            error["httpStatus"] = exc.status
        if exc.code is not None:
            error["apiCode"] = exc.code
        receipt.finish("FAIL", error)
        emit_receipt(receipt, secrets)
        return 1
    except common.ContractError as exc:
        receipt.finish("FAIL", {"type": "CONTRACT_ERROR", "message": secrets.redact(str(exc))})
        emit_receipt(receipt, secrets)
        return 1
    except common.GateError as exc:
        receipt.finish("FAIL", {"type": "GATE_ERROR", "message": secrets.redact(str(exc))})
        emit_receipt(receipt, secrets)
        return 1
    except Exception as exc:  # Defensive: never forge PASS on an unknown failure.
        receipt.finish("FAIL", {
            "type": "UNEXPECTED_ERROR", "message": secrets.redact(str(exc))[:512]})
        emit_receipt(receipt, secrets)
        return 1
    receipt.finish("PASS")
    emit_receipt(receipt, secrets)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
