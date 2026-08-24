#!/usr/bin/env bash
set -euo pipefail

exec python3 - "$@" <<'PY'
from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

IMAGE_RE = re.compile(r"^(?P<repository>[a-z0-9][a-z0-9._/-]*):(?P<tag>[A-Za-z0-9_.-]+)@(?P<digest>sha256:[0-9a-f]{64})$")
parser = argparse.ArgumentParser()
parser.add_argument("manifest", type=pathlib.Path)
parser.add_argument("--expected-git-sha")
parser.add_argument("--emit-env", action="store_true")
args = parser.parse_args()

try:
    raw = json.loads(args.manifest.read_text(encoding="utf-8"))
    if set(raw) != {"schemaVersion", "release", "images"} or raw["schemaVersion"] != 1:
        raise ValueError("unsupported manifest structure/schemaVersion")
    release = raw["release"]
    if set(release) != {"version", "gitSha", "createdAt", "sourceRun"}:
        raise ValueError("release fields must be exact")
    if not re.fullmatch(r"[0-9a-f]{40}", release["gitSha"]):
        raise ValueError("release.gitSha must be a full lowercase SHA")
    if args.expected_git_sha and release["gitSha"] != args.expected_git_sha:
        raise ValueError("release.gitSha does not match expected SHA")
    if not isinstance(raw["images"], list) or len(raw["images"]) != 3:
        raise ValueError("manifest must contain exactly app/worker/web images")
    by_component = {}
    for item in raw["images"]:
        if set(item) != {"component", "repository", "tag", "digest", "immutableRef"}:
            raise ValueError("image fields must be exact")
        component = item["component"]
        if component in by_component:
            raise ValueError(f"duplicate component: {component}")
        match = IMAGE_RE.fullmatch(item["immutableRef"])
        if not match or match.group("tag").lower() == "latest":
            raise ValueError(f"{component}: invalid immutableRef")
        if any(item[key] != match.group(key) for key in ("repository", "tag", "digest")):
            raise ValueError(f"{component}: tag/digest fields disagree with immutableRef")
        if item["digest"] == "sha256:" + "0" * 64:
            raise ValueError(f"{component}: all-zero template digest is not deployable")
        by_component[component] = item
    if set(by_component) != {"app", "worker", "web"}:
        raise ValueError("components must be exactly app, worker, web")
except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as exc:
    print(f"image manifest: FAIL: {exc}", file=sys.stderr)
    sys.exit(1)

if args.emit_env:
    for component in ("app", "worker", "web"):
        print(f"FRAMEFLOW_{component.upper()}_IMAGE={by_component[component]['immutableRef']}")
else:
    print(f"image manifest: PASS ({release['version']} {release['gitSha']})")
PY
