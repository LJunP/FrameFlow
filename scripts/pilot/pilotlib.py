#!/usr/bin/env python3
"""Shared safety, hashing, and serialization helpers for F11.

Reading order: ``run_synthetic_rehearsal.py`` ->
``generate_synthetic_dataset.py`` -> ``validate_pilot_package.py`` ->
``calculate_metrics.py`` -> ``generate_report.py`` -> this module.
"""

from __future__ import annotations

import csv
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import tempfile
from typing import Any, Iterable


CLASSIFICATION = "SYNTHETIC_REHEARSAL"
REPO_ROOT = Path(__file__).resolve().parents[2]
PILOT_ROOT = REPO_ROOT / "experiments" / "pilot"
GENERATED_ROOT = PILOT_ROOT / "generated"
EVIDENCE_ROOT = PILOT_ROOT / "evidence"

# Build the owner-only state names in two pieces so rehearsal artefacts cannot
# accidentally acquire them through source-template substitution.
OWNER_ONLY_STATE_NAMES = (
    "PILOT" + "_VALUE_PROVEN",
    "PROJECT" + "_COMPLETE",
)


class PilotError(RuntimeError):
    """A controlled pilot tooling failure suitable for a concise receipt."""


def require_repo_root() -> Path:
    """Fail closed before any write when the script is not in FrameFlow Select."""
    sentinels = (
        REPO_ROOT / "AGENTS.md",
        REPO_ROOT / "docs" / "01-产品与领域设计.md",
        REPO_ROOT / "docs" / "03-开发与学习路线.md",
    )
    missing = [str(path) for path in sentinels if not path.is_file()]
    if missing:
        raise PilotError(f"repository sentinel missing: {missing[0]}")
    return REPO_ROOT


def ensure_descendant(path: Path, parent: Path, label: str) -> Path:
    """Resolve a prospective path and prove it stays below an allowed root."""
    parent_resolved = parent.resolve(strict=True)
    resolved = path.expanduser().resolve(strict=False)
    try:
        relative = resolved.relative_to(parent_resolved)
    except ValueError as exc:
        raise PilotError(f"{label} must stay below {parent_resolved}") from exc
    if not relative.parts:
        raise PilotError(f"{label} must not be the allowed root itself")
    return resolved


def reset_output_directory(path: Path, parent: Path, label: str) -> Path:
    """Replace one exact generated/evidence directory after strict path guards."""
    resolved = ensure_descendant(path, parent, label)
    if resolved.is_symlink():
        raise PilotError(f"{label} must not be a symlink")
    # ★ 核心：只允许清理 experiments/pilot 下的一个具名后代；若去掉父路径与
    # symlink 双重校验，重跑彩排可能误删仓库或用户目录。
    if resolved.exists():
        if not resolved.is_dir():
            raise PilotError(f"{label} exists but is not a directory")
        shutil.rmtree(resolved)
    resolved.mkdir(parents=True, exist_ok=False)
    return resolved


def atomic_write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink():
        raise PilotError(f"refusing to overwrite symlink: {path}")
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as handle:
            handle.write(text)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except BaseException:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
        raise


def write_json(path: Path, value: Any) -> None:
    payload = json.dumps(
        value,
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
        allow_nan=False,
    )
    atomic_write_text(path, payload + "\n")


def read_json(path: Path) -> Any:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise PilotError(f"invalid JSON input: {path}") from exc


def read_csv(path: Path) -> list[dict[str, str]]:
    try:
        with path.open("r", encoding="utf-8-sig", newline="") as handle:
            reader = csv.DictReader(handle)
            if not reader.fieldnames:
                raise PilotError(f"CSV has no header: {path}")
            return [dict(row) for row in reader]
    except (OSError, UnicodeDecodeError, csv.Error) as exc:
        raise PilotError(f"invalid CSV input: {path}") from exc


def write_csv(path: Path, fieldnames: list[str], rows: Iterable[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.is_symlink():
        raise PilotError(f"refusing to overwrite symlink: {path}")
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="raise")
            writer.writeheader()
            writer.writerows(rows)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
    except BaseException:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
        raise


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def sha256_json(value: Any) -> str:
    canonical = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def artifact_record(path: Path, root: Path = REPO_ROOT) -> dict[str, Any]:
    resolved = path.resolve(strict=True)
    try:
        relative = resolved.relative_to(root.resolve(strict=True))
    except ValueError as exc:
        raise PilotError(f"artifact escapes repository: {path}") from exc
    if not resolved.is_file() or path.is_symlink():
        raise PilotError(f"artifact must be a regular non-symlink file: {path}")
    return {
        "path": relative.as_posix(),
        "sizeBytes": resolved.stat().st_size,
        "sha256": sha256_file(resolved),
    }


def collect_artifacts(directory: Path, *, exclude_names: set[str] | None = None) -> list[dict[str, Any]]:
    excluded = exclude_names or set()
    return [
        artifact_record(path)
        for path in sorted(directory.rglob("*"))
        if path.is_file() and path.name not in excluded
    ]


def parse_optional_bool(value: str | None, label: str) -> bool | None:
    normalized = (value or "").strip().lower()
    if normalized == "":
        return None
    if normalized in {"true", "1", "yes"}:
        return True
    if normalized in {"false", "0", "no"}:
        return False
    raise PilotError(f"{label} must be true, false, or blank")


def finite_number(value: Any, label: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise PilotError(f"{label} must be a number")
    parsed = float(value)
    if not math.isfinite(parsed):
        raise PilotError(f"{label} must be finite")
    return parsed


def assert_no_owner_only_states(text: str, label: str) -> None:
    for token in OWNER_ONLY_STATE_NAMES:
        if token in text:
            raise PilotError(f"{label} contains an owner-only project state")


def relative_repo_path(path: Path) -> str:
    try:
        return path.resolve(strict=False).relative_to(REPO_ROOT.resolve(strict=True)).as_posix()
    except ValueError as exc:
        raise PilotError(f"path escapes repository: {path}") from exc
