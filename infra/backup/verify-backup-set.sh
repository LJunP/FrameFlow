#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

[[ $# -eq 2 && "$1" == "--backup-dir" ]] || ff_die "Usage: $0 --backup-dir <absolute path>"
backup_dir="$2"
ff_safe_output_dir "$backup_dir"
[[ -d "$backup_dir" && ! -L "$backup_dir" ]] || ff_die "backup directory not found"
[[ -f "$backup_dir/manifest.json" && ! -L "$backup_dir/manifest.json" ]] ||
  ff_die "regular non-symlink manifest missing"
[[ -s "$backup_dir/SHA256SUMS" && ! -L "$backup_dir/SHA256SUMS" ]] ||
  ff_die "regular non-empty non-symlink SHA256SUMS missing"

artifact_type="$(ff_read_manifest_field "$backup_dir/manifest.json" artifactType)"
case "$artifact_type" in
  postgres-custom-dump)
    [[ -s "$backup_dir/dump.custom" && ! -L "$backup_dir/dump.custom" ]] ||
      ff_die "PostgreSQL dump missing, empty, or symlinked"
    [[ -f "$backup_dir/restore-list.txt" && ! -L "$backup_dir/restore-list.txt" ]] ||
      ff_die "PostgreSQL restore list missing or symlinked"
    ;;
  minio-current-objects)
    [[ -d "$backup_dir/objects" && ! -L "$backup_dir/objects" ]] ||
      ff_die "MinIO objects directory missing or symlinked"
    [[ -f "$backup_dir/object-inventory.jsonl" && ! -L "$backup_dir/object-inventory.jsonl" ]] ||
      ff_die "MinIO inventory missing or symlinked"
    ;;
  *) ff_die "unsupported artifactType: $artifact_type" ;;
esac

FRAMEFLOW_BACKUP_DIR="$backup_dir" FRAMEFLOW_ARTIFACT_TYPE="$artifact_type" python3 - <<'PY'
import hashlib
import os
import re
import stat
from pathlib import Path
from pathlib import PurePosixPath

root = Path(os.environ["FRAMEFLOW_BACKUP_DIR"])
artifact_type = os.environ["FRAMEFLOW_ARTIFACT_TYPE"]
checksum_file = root / "SHA256SUMS"
expected = {}

try:
    checksum_text = checksum_file.read_bytes().decode("utf-8")
except UnicodeDecodeError as exc:
    raise SystemExit(f"SHA256SUMS is not UTF-8: {exc}") from exc
if not checksum_text:
    raise SystemExit("SHA256SUMS is empty")
if "\x00" in checksum_text or "\r" in checksum_text:
    raise SystemExit("SHA256SUMS contains a forbidden control character")

lines = checksum_text[:-1].split("\n") if checksum_text.endswith("\n") else checksum_text.split("\n")
if not lines or any(not line for line in lines):
    raise SystemExit("SHA256SUMS contains no entries or an empty entry")

line_pattern = re.compile(r"^([0-9a-f]{64})  (.+)$")
for number, line in enumerate(lines, start=1):
    match = line_pattern.fullmatch(line)
    if match is None:
        raise SystemExit(f"malformed checksum entry at line {number}")
    digest, relative = match.groups()
    normalized = PurePosixPath(relative)
    if (relative.startswith("/") or relative == "SHA256SUMS"
            or normalized.as_posix() != relative
            or any(part in ("", ".", "..") for part in normalized.parts)):
        raise SystemExit(f"unsafe checksum path: {relative}")
    if relative in expected:
        raise SystemExit(f"duplicate checksum path: {relative}")
    expected[relative] = digest


def collect_actual_files() -> set[str]:
    result: set[str] = set()
    pending = [root]
    while pending:
        directory = pending.pop()
        with os.scandir(directory) as entries:
            for entry in entries:
                path = Path(entry.path)
                relative = path.relative_to(root).as_posix()
                if entry.is_symlink():
                    raise SystemExit(f"symlink is not allowed in backup set: {relative}")
                if entry.is_dir(follow_symlinks=False):
                    pending.append(path)
                    continue
                if not entry.is_file(follow_symlinks=False):
                    raise SystemExit(f"special file is not allowed in backup set: {relative}")
                if relative != "SHA256SUMS":
                    result.add(relative)
    return result


actual_files = collect_actual_files()
expected_files = set(expected)
if expected_files != actual_files:
    omitted = sorted(actual_files - expected_files)
    missing = sorted(expected_files - actual_files)
    raise SystemExit(
        f"checksum file set mismatch: omitted={omitted}, missing={missing}")

required = {"manifest.json"}
if artifact_type == "postgres-custom-dump":
    required.update({"dump.custom", "restore-list.txt"})
elif artifact_type == "minio-current-objects":
    required.add("object-inventory.jsonl")
missing_required = sorted(required - expected_files)
if missing_required:
    raise SystemExit(f"required artifacts are not checksummed: {missing_required}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    try:
        info = os.fstat(descriptor)
        if not stat.S_ISREG(info.st_mode):
            raise SystemExit(f"artifact is no longer a regular file: {path}")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    finally:
        os.close(descriptor)
    return digest.hexdigest()


for relative, digest in expected.items():
    if sha256(root / relative) != digest:
        raise SystemExit(f"checksum mismatch: {relative}")
print(f"verified exact set of {len(expected)} files")
PY

echo "F10 backup verification: PASS ($artifact_type, $backup_dir)"
