#!/usr/bin/env bash
set -euo pipefail

# ★ 核心：manifest 同时保存人可读 tag 与 Registry digest；部署只消费 immutableRef。
# 若只保存 tag，Registry 上同名 tag 被覆盖后，回滚无法证明拉到的是原镜像。
exec python3 - "$@" <<'PY'
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import pathlib
import re
import tempfile

IMAGE_RE = re.compile(r"^(?P<repository>[a-z0-9][a-z0-9._/-]*):(?P<tag>[A-Za-z0-9_.-]+)@(?P<digest>sha256:[0-9a-f]{64})$")

parser = argparse.ArgumentParser()
parser.add_argument("--version", required=True)
parser.add_argument("--git-sha", required=True)
parser.add_argument("--app-image", required=True)
parser.add_argument("--worker-image", required=True)
parser.add_argument("--web-image", required=True)
parser.add_argument("--output", required=True, type=pathlib.Path)
parser.add_argument("--source-run", default="local")
args = parser.parse_args()

if not re.fullmatch(r"[0-9a-f]{40}", args.git_sha):
    parser.error("--git-sha must be a full 40-character lowercase Git SHA")
if not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._+-]{0,63}", args.version):
    parser.error("--version contains unsafe characters")

images = []
for component, ref in (("app", args.app_image), ("worker", args.worker_image), ("web", args.web_image)):
    match = IMAGE_RE.fullmatch(ref)
    if not match or match.group("tag").lower() == "latest":
        parser.error(f"{component} image must be repository:tag@sha256:digest and not latest")
    images.append({
        "component": component,
        "repository": match.group("repository"),
        "tag": match.group("tag"),
        "digest": match.group("digest"),
        "immutableRef": ref,
    })

epoch = os.environ.get("SOURCE_DATE_EPOCH")
if epoch is not None:
    created = dt.datetime.fromtimestamp(int(epoch), tz=dt.timezone.utc)
else:
    created = dt.datetime.now(tz=dt.timezone.utc)
manifest = {
    "schemaVersion": 1,
    "release": {
        "version": args.version,
        "gitSha": args.git_sha,
        "createdAt": created.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "sourceRun": args.source_run,
    },
    "images": images,
}

args.output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.NamedTemporaryFile("w", encoding="utf-8", dir=args.output.parent, delete=False) as handle:
    json.dump(manifest, handle, ensure_ascii=False, indent=2, sort_keys=True)
    handle.write("\n")
    temp = pathlib.Path(handle.name)
os.replace(temp, args.output)
print(args.output)
PY
