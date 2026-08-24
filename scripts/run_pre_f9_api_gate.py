#!/usr/bin/env python3
"""Run the reproducible local API correctness gate required before F9.

The script deliberately uses only Python's standard library. Runtime progress is
written to stderr; stdout contains exactly one JSON receipt on PASS or FAIL.
"""

from __future__ import annotations

import argparse
import csv
import getpass
import hashlib
import io
import ipaddress
import json
import os
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
import sys
import time
from typing import Any
import urllib.error
import urllib.parse
import urllib.request
import uuid


REPO_ROOT = Path(__file__).resolve().parents[1]
FIXTURE_ROOT = REPO_ROOT / "experiments" / "fixtures" / "pre-f9-correctness"
MEDIA_ROOT = REPO_ROOT / "experiments" / "fixtures" / "generated" / "pre-f9-correctness"
DEFAULT_API_BASE = "http://127.0.0.1:18080/api/v1"
DEFAULT_EMAIL = "frameflow-pre-f9-gate@example.test"
TERMINAL_CANDIDATE_STATUSES = {
    "ANALYZED",
    "REVIEW_REQUIRED",
    "AUTO_REJECT",
    "ANALYSIS_ERROR",
    "INVALID",
}


@dataclass(frozen=True, slots=True)
class Fixture:
    key: str
    file_name: str
    expected_status: str


FIXTURES = (
    Fixture("valid_a", "valid-motion-a.mp4", "ANALYZED"),
    Fixture("valid_a_copy", "valid-motion-a-exact-copy.mp4", "ANALYZED"),
    Fixture("valid_b", "valid-motion-b.mp4", "ANALYZED"),
    Fixture("too_short", "too-short.mp4", "AUTO_REJECT"),
    Fixture("invalid_container", "invalid-container.mp4", "ANALYSIS_ERROR"),
)


class GateError(Exception):
    """Base class for a controlled, receipt-safe gate failure."""


class ContractError(GateError):
    """The local fixture or API response violated the expected contract."""


class ApiError(GateError):
    """A sanitized HTTP failure; response bodies and signed URLs are omitted."""

    def __init__(self, message: str, *, status: int | None = None, code: str | None = None):
        super().__init__(message)
        self.status = status
        self.code = code


class Secrets:
    """Best-effort redaction for unexpected exception messages."""

    def __init__(self) -> None:
        self._values: set[str] = set()

    def add(self, value: str | None) -> None:
        if value and len(value) >= 4:
            self._values.add(value)

    def redact(self, text: str) -> str:
        redacted = text
        for value in sorted(self._values, key=len, reverse=True):
            redacted = redacted.replace(value, "[REDACTED]")
        return redacted

    def scrub(self, value: Any) -> Any:
        """Recursively redact all credential values before stdout serialization."""
        if isinstance(value, str):
            return self.redact(value)
        if isinstance(value, dict):
            return {key: self.scrub(item) for key, item in value.items()}
        if isinstance(value, list):
            return [self.scrub(item) for item in value]
        return value


class Receipt:
    """Accumulates machine-readable evidence without retaining credentials."""

    def __init__(self, api_base: str) -> None:
        self.started_monotonic = time.monotonic()
        self.data: dict[str, Any] = {
            "schemaVersion": "frameflow.pre-f9-api-gate.v1",
            "status": "RUNNING",
            "startedAt": utc_now(),
            "apiBase": safe_url(api_base),
            "providerPolicy": {
                "profile": "deterministic-profile.json",
                "realProviderCallsAllowed": False,
            },
            "checks": [],
            "fixtures": {},
            "resources": {},
            "candidates": {},
            "analysis": {},
            "ranking": {},
            "selection": {},
            "exports": {},
        }

    def require(self, name: str, condition: bool, details: dict[str, Any] | None = None) -> None:
        entry: dict[str, Any] = {"name": name, "status": "PASS" if condition else "FAIL"}
        if details:
            entry["details"] = details
        self.data["checks"].append(entry)
        if not condition:
            raise ContractError(f"contract check failed: {name}")

    def finish(self, status: str, error: dict[str, Any] | None = None) -> None:
        self.data["status"] = status
        self.data["finishedAt"] = utc_now()
        self.data["durationMs"] = round((time.monotonic() - self.started_monotonic) * 1000)
        if error:
            self.data["error"] = error


class ApiClient:
    def __init__(self, base_url: str, timeout: float, secrets: Secrets):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.secrets = secrets
        self.access_token: str | None = None
        # Local gates must not accidentally route loopback traffic through a proxy.
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def set_access_token(self, token: str) -> None:
        if not token:
            raise ContractError("auth response accessToken is blank")
        self.access_token = token
        self.secrets.add(token)

    def json(
        self,
        method: str,
        path: str,
        *,
        expected: set[int],
        body: Any | None = None,
        headers: dict[str, str] | None = None,
        auth: bool = True,
        query: dict[str, Any] | None = None,
    ) -> Any:
        url = self._api_url(path, query)
        payload = None
        request_headers = {"Accept": "application/json"}
        if body is not None:
            payload = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            request_headers["Content-Type"] = "application/json"
        if headers:
            request_headers.update(headers)
        status, _response_headers, raw = self._request(
            method,
            url,
            expected=expected,
            data=payload,
            headers=request_headers,
            auth=auth,
        )
        if not raw:
            return None
        try:
            return json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise ContractError(
                f"{method} {self._endpoint_label(url)} returned non-JSON at HTTP {status}"
            ) from exc

    def bytes(
        self,
        method: str,
        path: str,
        *,
        expected: set[int],
        query: dict[str, Any] | None = None,
    ) -> tuple[dict[str, str], bytes]:
        url = self._api_url(path, query)
        _status, headers, raw = self._request(
            method,
            url,
            expected=expected,
            data=None,
            headers={"Accept": "application/json, text/csv"},
            auth=True,
        )
        return headers, raw

    def put_file(self, upload_url: str, path: Path, content_type: str) -> None:
        parsed = urllib.parse.urlsplit(upload_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname or parsed.username:
            raise ContractError("presigned uploadUrl is not a credential-free HTTP(S) URL")
        self.secrets.add(upload_url)
        self._request(
            "PUT",
            upload_url,
            expected={200, 201, 204},
            data=path.read_bytes(),
            headers={"Content-Type": content_type},
            auth=False,
        )

    def _api_url(self, path: str, query: dict[str, Any] | None) -> str:
        if not path.startswith("/") or "?" in path or "#" in path:
            raise ValueError(f"API path must be absolute and query-free: {path!r}")
        url = self.base_url + path
        if query:
            url += "?" + urllib.parse.urlencode(query)
        return url

    def _request(
        self,
        method: str,
        url: str,
        *,
        expected: set[int],
        data: bytes | None,
        headers: dict[str, str],
        auth: bool,
    ) -> tuple[int, dict[str, str], bytes]:
        request_headers = dict(headers)
        if auth:
            if self.access_token is None:
                raise ContractError("authenticated API request attempted before login")
            request_headers["Authorization"] = f"Bearer {self.access_token}"
        request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
        endpoint = self._endpoint_label(url)
        try:
            with self.opener.open(request, timeout=self.timeout) as response:
                status = int(response.status)
                raw = response.read()
                response_headers = {key.lower(): value for key, value in response.headers.items()}
        except urllib.error.HTTPError as exc:
            raw = exc.read(65536)
            code = self._error_code(raw)
            raise ApiError(
                f"HTTP {exc.code} {code or 'HTTP_ERROR'} at {method} {endpoint}",
                status=exc.code,
                code=code,
            ) from None
        except (urllib.error.URLError, TimeoutError, OSError) as exc:
            raise ApiError(f"network request failed at {method} {endpoint}") from exc
        if status not in expected:
            raise ApiError(
                f"unexpected HTTP {status} at {method} {endpoint}; expected {sorted(expected)}",
                status=status,
            )
        return status, response_headers, raw

    @staticmethod
    def _error_code(raw: bytes) -> str | None:
        try:
            parsed = json.loads(raw.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return None
        if isinstance(parsed, dict) and isinstance(parsed.get("code"), str):
            return parsed["code"][:96]
        return None

    @staticmethod
    def _endpoint_label(url: str) -> str:
        parsed = urllib.parse.urlsplit(url)
        return urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, parsed.path, "", ""))


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def eprint(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def safe_url(url: str) -> str:
    try:
        parsed = urllib.parse.urlsplit(url)
        host = parsed.hostname or ""
        port = parsed.port
    except ValueError:
        return "<invalid-api-base>"
    if ":" in host and not host.startswith("["):
        host = f"[{host}]"
    netloc = host
    if port is not None:
        netloc += f":{port}"
    return urllib.parse.urlunsplit((parsed.scheme, netloc, parsed.path, "", ""))


def normalize_api_base(value: str, allow_non_loopback: bool) -> str:
    try:
        parsed = urllib.parse.urlsplit(value.strip())
        # Accessing port validates malformed/non-numeric/out-of-range ports.
        parsed.port
    except ValueError as exc:
        raise ContractError("--api-base is not a valid URL") from exc
    if parsed.scheme not in {"http", "https"}:
        raise ContractError("--api-base scheme must be http or https")
    if not parsed.hostname or parsed.username is not None or parsed.password is not None:
        raise ContractError("--api-base must have a host and must not contain credentials")
    if parsed.query or parsed.fragment:
        raise ContractError("--api-base must not contain a query or fragment")
    if not allow_non_loopback and not is_loopback_host(parsed.hostname):
        raise ContractError(
            "--api-base is not loopback; pass --allow-non-loopback only for an explicitly trusted host"
        )
    path = parsed.path.rstrip("/")
    if path in {"", "/"}:
        path = "/api/v1"
    elif path != "/api/v1":
        raise ContractError("--api-base path must be empty or exactly /api/v1")
    normalized = urllib.parse.urlunsplit((parsed.scheme, parsed.netloc, path, "", ""))
    return normalized.rstrip("/")


def is_loopback_host(host: str) -> bool:
    if host.lower() == "localhost":
        return True
    try:
        return ipaddress.ip_address(host).is_loopback
    except ValueError:
        return False


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require_fixture_file(path: Path) -> Path:
    if path.is_symlink():
        raise ContractError(f"fixture must not be a symlink: {path.relative_to(REPO_ROOT)}")
    try:
        resolved = path.resolve(strict=True)
        resolved.relative_to(REPO_ROOT.resolve(strict=True))
    except (FileNotFoundError, ValueError) as exc:
        raise ContractError(f"fixture is missing or escapes the repository: {path}") from exc
    if not resolved.is_file():
        raise ContractError(f"fixture is not a regular file: {path.relative_to(REPO_ROOT)}")
    return resolved


def expect_dict(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ContractError(f"{label} must be a JSON object")
    return value


def expect_list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise ContractError(f"{label} must be a JSON array")
    return value


def integer_field(obj: dict[str, Any], field: str, label: str) -> int:
    value = obj.get(field)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ContractError(f"{label}.{field} must be an integer")
    return value


def string_field(obj: dict[str, Any], field: str, label: str) -> str:
    value = obj.get(field)
    if not isinstance(value, str) or not value:
        raise ContractError(f"{label}.{field} must be a non-empty string")
    return value


def load_fixtures(receipt: Receipt) -> tuple[str, dict[str, Any], dict[str, Path]]:
    brief_path = require_fixture_file(FIXTURE_ROOT / "brief.md")
    profile_path = require_fixture_file(FIXTURE_ROOT / "deterministic-profile.json")
    brief_bytes = brief_path.read_bytes()
    profile_bytes = profile_path.read_bytes()
    try:
        brief = brief_bytes.decode("utf-8")
        profile = json.loads(profile_bytes.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ContractError("Brief/Profile fixture is not valid UTF-8/JSON") from exc
    profile = expect_dict(profile, "deterministic profile")
    receipt.require("fixture.brief.non_blank", bool(brief.strip()))

    semantic = profile.get("semantic")
    semantic_enabled = isinstance(semantic, dict) and semantic.get("enabled") is True
    receipt.data["providerPolicy"]["semanticEnabled"] = semantic_enabled
    receipt.require("profile.real_provider_disabled", not semantic_enabled)
    dimensions = expect_dict(profile.get("dimensions"), "profile.dimensions")
    expected_dimensions = {
        "duration": {"min": 2, "max": 10, "severity": "BLOCKER"},
        "resolution": {"minWidth": 320, "minHeight": 180, "severity": "BLOCKER"},
        "fps": {"min": 20, "severity": "BLOCKER"},
    }
    for dimension, expected in expected_dimensions.items():
        actual = expect_dict(dimensions.get(dimension), f"profile.dimensions.{dimension}")
        receipt.require(
            f"profile.dimension.{dimension}",
            all(actual.get(key) == value for key, value in expected.items()),
            {"expected": expected},
        )
    duplicates = expect_dict(profile.get("duplicates"), "profile.duplicates")
    receipt.require("profile.duplicate_threshold", duplicates.get("hammingThreshold") == 6)

    media_paths: dict[str, Path] = {}
    media_receipt: list[dict[str, Any]] = []
    for fixture in FIXTURES:
        media_path = require_fixture_file(MEDIA_ROOT / fixture.file_name)
        size = media_path.stat().st_size
        digest = sha256_file(media_path)
        receipt.require(f"fixture.media.{fixture.key}.non_empty", size > 0)
        media_paths[fixture.key] = media_path
        media_receipt.append(
            {
                "key": fixture.key,
                "fileName": fixture.file_name,
                "sizeBytes": size,
                "sha256": digest,
                "expectedStatus": fixture.expected_status,
            }
        )
    hashes = {item["key"]: item["sha256"] for item in media_receipt}
    receipt.require("fixture.exact_copy.same_bytes", hashes["valid_a"] == hashes["valid_a_copy"])
    receipt.require("fixture.valid_b.distinct_bytes", hashes["valid_b"] != hashes["valid_a"])
    receipt.data["fixtures"] = {
        "brief": {"path": str(brief_path.relative_to(REPO_ROOT)), "sha256": sha256_bytes(brief_bytes)},
        "profile": {
            "path": str(profile_path.relative_to(REPO_ROOT)),
            "sha256": sha256_bytes(profile_bytes),
        },
        "media": media_receipt,
    }
    return brief, profile, media_paths


def authenticate(
    client: ApiClient,
    receipt: Receipt,
    secrets: Secrets,
    email: str,
    password: str,
    run_suffix: str,
) -> None:
    if not (8 <= len(password) <= 72):
        raise ContractError("password must contain 8..72 characters")
    secrets.add(password)
    register_body = {
        "email": email,
        "password": password,
        "displayName": "Pre-F9 API Gate",
        "teamName": "Pre-F9 Gate",
    }
    try:
        response = client.json(
            "POST",
            "/auth/register",
            expected={200},
            body=register_body,
            headers={"Idempotency-Key": f"pre-f9-{run_suffix}-{uuid.uuid4()}"},
            auth=False,
        )
        auth_mode = "REGISTERED"
    except ApiError as exc:
        if exc.status != 409 or exc.code != "EMAIL_ALREADY_EXISTS":
            raise
        response = client.json(
            "POST",
            "/auth/login",
            expected={200},
            body={"email": email, "password": password},
            auth=False,
        )
        auth_mode = "LOGGED_IN"
    auth = expect_dict(response, "auth response")
    user = expect_dict(auth.get("user"), "auth.user")
    team = expect_dict(auth.get("team"), "auth.team")
    token = string_field(auth, "accessToken", "auth")
    refresh_token = auth.get("refreshToken")
    if isinstance(refresh_token, str):
        secrets.add(refresh_token)
    # ★ 核心：令牌只进入内存中的客户端头；receipt、日志和磁盘永不接触令牌。
    client.set_access_token(token)
    auth.pop("accessToken", None)
    auth.pop("refreshToken", None)
    receipt.data["auth"] = {
        "mode": auth_mode,
        "emailSha256": hashlib.sha256(email.lower().encode("utf-8")).hexdigest(),
        "userId": integer_field(user, "id", "auth.user"),
        "teamId": integer_field(team, "id", "auth.team"),
        "role": string_field(team, "role", "auth.team"),
    }
    receipt.require("auth.access_token.accepted", client.access_token is not None)


def create_resources(
    client: ApiClient,
    receipt: Receipt,
    brief: str,
    profile: dict[str, Any],
    run_suffix: str,
) -> int:
    project = expect_dict(
        client.json(
            "POST",
            "/projects",
            expected={201},
            body={
                "name": f"Pre-F9 API Gate {run_suffix}",
                "description": "Synthetic local correctness gate; not pilot-value evidence.",
            },
        ),
        "create project response",
    )
    project_id = integer_field(project, "id", "project")
    brief_response = expect_dict(
        client.json(
            "POST",
            f"/projects/{project_id}/briefs",
            expected={201},
            body={"content": brief},
        ),
        "publish brief response",
    )
    brief_id = integer_field(brief_response, "id", "brief")
    receipt.require("brief.project_binding", brief_response.get("projectId") == project_id)

    profile_response = expect_dict(
        client.json(
            "POST",
            "/quality-profiles",
            expected={201},
            body={
                "name": f"Pre-F9 Deterministic {run_suffix}",
                "description": "No semantic Provider calls are permitted.",
                "spec": json.dumps(profile, ensure_ascii=False, sort_keys=True, separators=(",", ":")),
            },
        ),
        "create profile response",
    )
    profile_id = integer_field(profile_response, "id", "profile")
    receipt.require("profile.initial_version", profile_response.get("latestVersion") == 1)

    batch = expect_dict(
        client.json(
            "POST",
            "/batches",
            expected={201},
            body={"projectId": project_id, "profileId": profile_id, "capacity": len(FIXTURES)},
        ),
        "create batch response",
    )
    batch_id = integer_field(batch, "id", "batch")
    receipt.require("batch.project_binding", batch.get("projectId") == project_id)
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


def upload_candidates(
    client: ApiClient,
    receipt: Receipt,
    batch_id: int,
    media_paths: dict[str, Path],
) -> dict[str, int]:
    candidate_ids: dict[str, int] = {}
    for fixture in FIXTURES:
        path = media_paths[fixture.key]
        registered = expect_dict(
            client.json(
                "POST",
                f"/batches/{batch_id}/candidates",
                expected={201},
                body={
                    "fileName": fixture.file_name,
                    "contentType": "video/mp4",
                    "sizeBytes": path.stat().st_size,
                },
            ),
            f"register candidate {fixture.key}",
        )
        candidate_id = integer_field(registered, "candidateId", f"candidate.{fixture.key}")
        receipt.require(f"upload.{fixture.key}.simple_mode", registered.get("mode") == "SIMPLE")
        upload_url = string_field(registered, "uploadUrl", f"candidate.{fixture.key}")
        client.put_file(upload_url, path, "video/mp4")
        completed = expect_dict(
            client.json(
                "POST",
                f"/candidates/{candidate_id}/complete",
                expected={200},
                body={},
            ),
            f"complete candidate {fixture.key}",
        )
        receipt.require(f"upload.{fixture.key}.completed", completed.get("status") == "UPLOADED")
        receipt.require(
            f"upload.{fixture.key}.candidate_binding", completed.get("candidateId") == candidate_id
        )
        candidate_ids[fixture.key] = candidate_id
        eprint(f"uploaded fixture {fixture.key} as candidate {candidate_id}")
    receipt.require("upload.unique_candidate_ids", len(set(candidate_ids.values())) == len(FIXTURES))
    receipt.data["candidates"] = {
        fixture.key: {
            "candidateId": candidate_ids[fixture.key],
            "fileName": fixture.file_name,
            "expectedStatus": fixture.expected_status,
        }
        for fixture in FIXTURES
    }
    return candidate_ids


def close_analyze_and_wait(
    client: ApiClient,
    receipt: Receipt,
    batch_id: int,
    candidate_ids: dict[str, int],
    timeout: float,
    poll_interval: float,
) -> dict[str, dict[str, Any]]:
    closed = expect_dict(
        client.json("POST", f"/batches/{batch_id}/close", expected={200}, body={}),
        "close batch response",
    )
    receipt.require("batch.closed", closed.get("status") == "CLOSED")
    dispatched = expect_dict(
        client.json("POST", f"/batches/{batch_id}/analyze", expected={202}, body={}),
        "analyze batch response",
    )
    receipt.require("analysis.batch_binding", dispatched.get("batchId") == batch_id)
    receipt.require("analysis.dispatched_all", dispatched.get("dispatched") == len(FIXTURES))
    receipt.require("analysis.skipped_none", dispatched.get("skipped") == 0)

    expected_ids = set(candidate_ids.values())
    deadline = time.monotonic() + timeout
    previous_summary = ""
    by_id: dict[int, dict[str, Any]] = {}
    while True:
        page = expect_dict(
            client.json(
                "GET",
                f"/batches/{batch_id}/candidates",
                expected={200},
                query={"page": 0, "size": 200},
            ),
            "candidate page",
        )
        items = expect_list(page.get("items"), "candidate page.items")
        by_id = {}
        for index, item in enumerate(items):
            candidate = expect_dict(item, f"candidate page.items[{index}]")
            by_id[integer_field(candidate, "id", f"candidate[{index}]")] = candidate
        if set(by_id) != expected_ids or page.get("total") != len(expected_ids):
            raise ContractError("candidate page does not contain exactly the five registered candidates")
        summary = ", ".join(
            f"{key}={by_id[candidate_id].get('status')}"
            for key, candidate_id in candidate_ids.items()
        )
        if summary != previous_summary:
            eprint(f"analysis progress: {summary}")
            previous_summary = summary
        if all(by_id[candidate_id].get("status") in TERMINAL_CANDIDATE_STATUSES for candidate_id in expected_ids):
            break
        if time.monotonic() >= deadline:
            raise GateError(f"analysis did not reach terminal states within {timeout:g} seconds")
        time.sleep(poll_interval)

    actual_statuses: dict[str, str] = {}
    for fixture in FIXTURES:
        actual = by_id[candidate_ids[fixture.key]].get("status")
        receipt.require(
            f"analysis.status.{fixture.key}",
            actual == fixture.expected_status,
            {"expected": fixture.expected_status, "actual": actual},
        )
        actual_statuses[fixture.key] = str(actual)
        receipt.data["candidates"][fixture.key]["actualStatus"] = actual
    receipt.data["analysis"] = {
        "dispatched": dispatched.get("dispatched"),
        "skipped": dispatched.get("skipped"),
        "terminalStatuses": actual_statuses,
    }
    return {key: by_id[candidate_id] for key, candidate_id in candidate_ids.items()}


def validate_findings(
    client: ApiClient,
    receipt: Receipt,
    candidate_ids: dict[str, int],
) -> None:
    findings_by_key: dict[str, list[dict[str, Any]]] = {}
    total_findings = 0
    for key, candidate_id in candidate_ids.items():
        raw = expect_list(
            client.json("GET", f"/candidates/{candidate_id}/findings", expected={200}),
            f"findings.{key}",
        )
        findings = [expect_dict(item, f"findings.{key}[{index}]") for index, item in enumerate(raw)]
        findings_by_key[key] = findings
        total_findings += len(findings)
        receipt.require(
            f"findings.{key}.no_semantic_verdict",
            all(finding.get("verdict") is None for finding in findings),
        )

    for key in ("valid_a", "valid_a_copy", "valid_b"):
        deterministic = {
            finding.get("dimension"): finding
            for finding in findings_by_key[key]
            if finding.get("dimension") in {"duration", "resolution", "fps"}
        }
        receipt.require(
            f"findings.{key}.deterministic_passes",
            set(deterministic) == {"duration", "resolution", "fps"}
            and all(finding.get("passed") is True for finding in deterministic.values()),
        )
    too_short_duration = [
        finding
        for finding in findings_by_key["too_short"]
        if finding.get("dimension") == "duration"
    ]
    receipt.require(
        "findings.too_short.blocker_duration_failure",
        len(too_short_duration) == 1
        and too_short_duration[0].get("passed") is False
        and too_short_duration[0].get("severity") == "BLOCKER",
    )
    receipt.require(
        "findings.invalid_container.empty",
        findings_by_key["invalid_container"] == [],
    )
    receipt.data["analysis"]["findingCounts"] = {
        key: len(findings) for key, findings in findings_by_key.items()
    }
    receipt.data["analysis"]["totalFindings"] = total_findings


def rank_and_validate(
    client: ApiClient,
    receipt: Receipt,
    batch_id: int,
    candidate_ids: dict[str, int],
) -> int:
    summary = expect_dict(
        client.json("POST", f"/batches/{batch_id}/rank", expected={201}, body={}),
        "rank response",
    )
    snapshot_id = integer_field(summary, "snapshotId", "ranking summary")
    receipt.require("ranking.summary.ranked", summary.get("ranked") == 2)
    receipt.require("ranking.summary.clusters", summary.get("clusters") == 2)
    receipt.require("ranking.summary.excluded", summary.get("excluded") == 2)

    latest = expect_dict(
        client.json("GET", f"/batches/{batch_id}/ranking/latest", expected={200}),
        "latest ranking response",
    )
    receipt.require("ranking.snapshot_binding", latest.get("snapshotId") == snapshot_id)
    receipt.require("ranking.algorithm_version", latest.get("algorithmVersion") == "rank-v1")
    receipt.require("ranking.hamming_threshold", latest.get("hammingThreshold") == 6)
    entries_raw = expect_list(latest.get("entries"), "ranking.entries")
    entries: dict[int, dict[str, Any]] = {}
    for index, raw in enumerate(entries_raw):
        entry = expect_dict(raw, f"ranking.entries[{index}]")
        entries[integer_field(entry, "candidateId", f"ranking.entries[{index}]")] = entry
    receipt.require(
        "ranking.all_candidates_present",
        set(entries) == set(candidate_ids.values()) and len(entries_raw) == len(FIXTURES),
    )

    a_id = candidate_ids["valid_a"]
    copy_id = candidate_ids["valid_a_copy"]
    b_id = candidate_ids["valid_b"]
    short_id = candidate_ids["too_short"]
    invalid_id = candidate_ids["invalid_container"]
    a = entries[a_id]
    copy = entries[copy_id]
    b = entries[b_id]
    receipt.require(
        "ranking.valid_a.representative_rank_1",
        a.get("representative") is True
        and a.get("rankNo") == 1
        and a.get("score") == 100
        and a.get("excludedReason") is None,
    )
    receipt.require(
        "ranking.exact_copy.clustered",
        copy.get("representative") is False
        and copy.get("rankNo") == 0
        and copy.get("score") == 100
        and copy.get("clusterId") == a.get("clusterId")
        and copy.get("excludedReason") == f"DUPLICATE_OF_{a_id}",
    )
    receipt.require(
        "ranking.valid_b.separate_representative_rank_2",
        b.get("representative") is True
        and b.get("rankNo") == 2
        and b.get("score") == 100
        and b.get("clusterId") != a.get("clusterId")
        and b.get("excludedReason") is None,
    )
    receipt.require(
        "ranking.auto_reject_excluded",
        entries[short_id].get("excludedReason") == "STATUS_AUTO_REJECT"
        and entries[short_id].get("representative") is False
        and entries[short_id].get("rankNo") == 0,
    )
    receipt.require(
        "ranking.analysis_error_excluded",
        entries[invalid_id].get("excludedReason") == "STATUS_ANALYSIS_ERROR"
        and entries[invalid_id].get("representative") is False
        and entries[invalid_id].get("rankNo") == 0,
    )
    receipt.data["ranking"] = {
        "snapshotId": snapshot_id,
        "algorithmVersion": latest.get("algorithmVersion"),
        "ranked": summary.get("ranked"),
        "clusters": summary.get("clusters"),
        "excluded": summary.get("excluded"),
        "representativeCandidateIds": [a_id, b_id],
        "exactDuplicateCandidateId": copy_id,
    }
    return snapshot_id


def create_adjust_lock_export(
    client: ApiClient,
    receipt: Receipt,
    batch_id: int,
    snapshot_id: int,
    candidate_ids: dict[str, int],
) -> None:
    created = expect_dict(
        client.json(
            "POST",
            f"/batches/{batch_id}/selections",
            expected={201},
            body={"topK": 2, "snapshotId": snapshot_id},
        ),
        "create selection response",
    )
    selection_id = integer_field(created, "id", "selection")
    receipt.require("selection.created_draft", created.get("status") == "DRAFT")
    receipt.require("selection.snapshot_binding", created.get("snapshotId") == snapshot_id)

    a_id = candidate_ids["valid_a"]
    copy_id = candidate_ids["valid_a_copy"]
    client.json(
        "POST",
        f"/selections/{selection_id}/items",
        expected={200},
        body={"candidateId": a_id, "action": "EXCLUDE", "note": "pre-f9 gate human exclude"},
    )
    adjusted = expect_dict(
        client.json(
            "POST",
            f"/selections/{selection_id}/items",
            expected={200},
            body={"candidateId": copy_id, "action": "INCLUDE", "note": "pre-f9 gate human include"},
        ),
        "adjust selection response",
    )
    items_raw = expect_list(adjusted.get("items"), "selection.items")
    items: dict[int, dict[str, Any]] = {}
    for index, raw in enumerate(items_raw):
        item = expect_dict(raw, f"selection.items[{index}]")
        items[integer_field(item, "candidateId", f"selection.items[{index}]")] = item
    receipt.require(
        "selection.human_exclude",
        a_id in items
        and items[a_id].get("machinePick") is True
        and items[a_id].get("humanAction") == "EXCLUDE",
    )
    receipt.require(
        "selection.human_include",
        copy_id in items
        and items[copy_id].get("machinePick") is False
        and items[copy_id].get("humanAction") == "INCLUDE",
    )

    locked = expect_dict(
        client.json("POST", f"/selections/{selection_id}/lock", expected={200}, body={}),
        "lock selection response",
    )
    receipt.require(
        "selection.locked",
        locked.get("status") == "LOCKED"
        and isinstance(locked.get("lockedAt"), str)
        and bool(locked.get("lockedAt")),
    )

    json_headers, json_bytes = client.bytes(
        "GET", f"/selections/{selection_id}/export", expected={200}, query={"format": "json"}
    )
    csv_headers, csv_bytes = client.bytes(
        "GET", f"/selections/{selection_id}/export", expected={200}, query={"format": "csv"}
    )
    try:
        json_rows = expect_list(json.loads(json_bytes.decode("utf-8")), "JSON export")
        csv_text = csv_bytes.decode("utf-8-sig")
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ContractError("selection export is not valid UTF-8/JSON") from exc
    json_objects = [expect_dict(row, f"JSON export[{index}]") for index, row in enumerate(json_rows)]
    reader = csv.DictReader(io.StringIO(csv_text, newline=""))
    csv_rows = list(reader)
    expected_header = [
        "rank",
        "candidate_id",
        "score",
        "cluster_id",
        "machine_pick",
        "human_action",
        "note",
    ]
    receipt.require("export.csv.header", reader.fieldnames == expected_header)
    receipt.require("export.row_count", len(json_objects) == len(csv_rows) == 3)
    json_by_id = {integer_field(row, "candidateId", "JSON export row"): row for row in json_objects}
    try:
        csv_by_id = {int(row["candidate_id"]): row for row in csv_rows}
    except (KeyError, TypeError, ValueError) as exc:
        raise ContractError("CSV export candidate_id is missing or non-numeric") from exc
    receipt.require("export.same_candidate_set", set(json_by_id) == set(csv_by_id) == set(items))
    receipt.require(
        "export.json.human_actions",
        json_by_id[a_id].get("humanAction") == "EXCLUDE"
        and json_by_id[copy_id].get("humanAction") == "INCLUDE",
    )
    receipt.require(
        "export.csv.human_actions",
        csv_by_id[a_id].get("human_action") == "EXCLUDE"
        and csv_by_id[copy_id].get("human_action") == "INCLUDE",
    )
    cross_format_matches = True
    for candidate_id, json_row in json_by_id.items():
        csv_row = csv_by_id[candidate_id]
        expected_csv = {
            "rank": str(json_row.get("rank")),
            "candidate_id": str(json_row.get("candidateId")),
            "score": str(json_row.get("score")),
            "cluster_id": str(json_row.get("clusterId")),
            "machine_pick": str(json_row.get("machinePick")).lower(),
            "human_action": "" if json_row.get("humanAction") is None else str(json_row.get("humanAction")),
            "note": "" if json_row.get("note") is None else str(json_row.get("note")),
        }
        if csv_row != expected_csv:
            cross_format_matches = False
            break
    receipt.require("export.json_csv.field_equivalence", cross_format_matches)
    receipt.require(
        "export.content_types",
        json_headers.get("content-type", "").lower().startswith("application/json")
        and csv_headers.get("content-type", "").lower().startswith("text/csv"),
    )
    receipt.data["selection"] = {
        "selectionId": selection_id,
        "status": "LOCKED",
        "topK": created.get("topK"),
        "itemCount": len(items),
        "humanActions": {"INCLUDE": [copy_id], "EXCLUDE": [a_id]},
    }
    receipt.data["exports"] = {
        "json": {"rowCount": len(json_objects), "sizeBytes": len(json_bytes), "sha256": sha256_bytes(json_bytes)},
        "csv": {"rowCount": len(csv_rows), "sizeBytes": len(csv_bytes), "sha256": sha256_bytes(csv_bytes)},
    }


def run_gate(args: argparse.Namespace, receipt: Receipt, secrets: Secrets) -> None:
    brief, profile, media_paths = load_fixtures(receipt)
    password = args.password or os.environ.get("FRAMEFLOW_GATE_PASSWORD")
    if password is None:
        if not sys.stdin.isatty():
            raise ContractError(
                "password is required via --password, FRAMEFLOW_GATE_PASSWORD, or an interactive prompt"
            )
        password = getpass.getpass("FrameFlow local gate password (not echoed): ")
    secrets.add(password)
    run_suffix = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    client = ApiClient(args.api_base, args.request_timeout, secrets)

    eprint("checking local FrameFlow API")
    ping = expect_dict(client.json("GET", "/ping", expected={200}, auth=False), "ping response")
    receipt.require("api.ping.app", ping.get("app") == "frameflow-select")
    authenticate(client, receipt, secrets, args.email, password, run_suffix)
    eprint("authenticated; credentials remain in memory only")
    batch_id = create_resources(client, receipt, brief, profile, run_suffix)
    candidate_ids = upload_candidates(client, receipt, batch_id, media_paths)
    close_analyze_and_wait(
        client,
        receipt,
        batch_id,
        candidate_ids,
        args.analysis_timeout,
        args.poll_interval,
    )
    validate_findings(client, receipt, candidate_ids)
    snapshot_id = rank_and_validate(client, receipt, batch_id, candidate_ids)
    create_adjust_lock_export(client, receipt, batch_id, snapshot_id, candidate_ids)


def positive_float(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be a number") from exc
    if parsed <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Run the local FrameFlow pre-F9 API main-path gate and emit one JSON receipt. "
            "The deterministic fixture never enables the semantic Provider."
        ),
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
        epilog=(
            "Prerequisites: generated pre-f9 media, local PostgreSQL/Redis/RabbitMQ/MinIO, "
            "the FrameFlow Java API, and one running Python Worker. Prefer "
            "FRAMEFLOW_GATE_PASSWORD over --password to avoid shell-history exposure."
        ),
    )
    parser.add_argument(
        "--api-base",
        default=DEFAULT_API_BASE,
        help="API root, either origin-only or ending exactly in /api/v1",
    )
    parser.add_argument("--email", default=DEFAULT_EMAIL, help="local test account email")
    parser.add_argument(
        "--password",
        default=None,
        help="local test account password; otherwise use FRAMEFLOW_GATE_PASSWORD or prompt",
    )
    parser.add_argument(
        "--allow-non-loopback",
        action="store_true",
        help="allow a non-loopback API host (disabled by default for safety)",
    )
    parser.add_argument(
        "--request-timeout",
        type=positive_float,
        default=15.0,
        help="timeout in seconds for each HTTP request",
    )
    parser.add_argument(
        "--analysis-timeout",
        type=positive_float,
        default=180.0,
        help="overall seconds to wait for Worker terminal statuses",
    )
    parser.add_argument(
        "--poll-interval",
        type=positive_float,
        default=1.0,
        help="seconds between candidate status polls",
    )
    return parser


def emit_receipt(receipt: Receipt, secrets: Secrets) -> None:
    """The only live-run stdout boundary; credentials are scrubbed recursively."""
    print(json.dumps(secrets.scrub(receipt.data), ensure_ascii=False, sort_keys=True))


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    secrets = Secrets()
    secrets.add(args.password)
    try:
        args.api_base = normalize_api_base(args.api_base, args.allow_non_loopback)
    except GateError as exc:
        receipt = Receipt(args.api_base)
        receipt.finish("FAIL", {"type": "CONFIG_ERROR", "message": secrets.redact(str(exc))})
        emit_receipt(receipt, secrets)
        return 2
    receipt = Receipt(args.api_base)
    try:
        run_gate(args, receipt, secrets)
    except ApiError as exc:
        error: dict[str, Any] = {"type": "API_ERROR", "message": secrets.redact(str(exc))}
        if exc.status is not None:
            error["httpStatus"] = exc.status
        if exc.code is not None:
            error["apiCode"] = exc.code
        receipt.finish("FAIL", error)
        emit_receipt(receipt, secrets)
        return 1
    except ContractError as exc:
        receipt.finish("FAIL", {"type": "CONTRACT_ERROR", "message": secrets.redact(str(exc))})
        emit_receipt(receipt, secrets)
        return 1
    except GateError as exc:
        receipt.finish("FAIL", {"type": "GATE_ERROR", "message": secrets.redact(str(exc))})
        emit_receipt(receipt, secrets)
        return 1
    except Exception as exc:  # Defensive receipt boundary; never forge PASS on unknown failure.
        receipt.finish(
            "FAIL",
            {"type": "UNEXPECTED_ERROR", "message": secrets.redact(str(exc))[:512]},
        )
        emit_receipt(receipt, secrets)
        return 1
    receipt.finish("PASS")
    emit_receipt(receipt, secrets)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
