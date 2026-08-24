#!/usr/bin/env bash
# 由入口脚本先解析绝对 BACKUP_ROOT，再设置 FRAMEFLOW_BACKUP_LIB_ROOT 后 source。
# ★ 核心：被 source 的脚本绝不从 $0 推导路径；$0 指向调用者，会把写入根带偏。

[[ -n "${FRAMEFLOW_BACKUP_LIB_ROOT:-}" ]] || {
  echo "ERROR: FRAMEFLOW_BACKUP_LIB_ROOT must be set by the entry script" >&2
  return 2
}
[[ -f "$FRAMEFLOW_BACKUP_LIB_ROOT/.f10-backup-root" ]] || {
  echo "ERROR: backup root sentinel missing: $FRAMEFLOW_BACKUP_LIB_ROOT" >&2
  return 2
}

ff_die() {
  echo "ERROR: $*" >&2
  exit 2
}

ff_require_environment() {
  local value="$1"
  [[ "$value" =~ ^(local|dev|staging|production)$ ]] ||
    ff_die "environment must be local, dev, staging, or production"
}

ff_require_identifier() {
  local label="$1" value="$2"
  [[ "$value" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$ ]] ||
    ff_die "$label contains unsafe characters: $value"
}

ff_require_project() {
  local environment="$1" project="$2"
  [[ "$project" =~ ^frameflow-[a-z0-9][a-z0-9-]{0,62}$ ]] ||
    ff_die "COMPOSE project must use the frameflow-* namespace"
  [[ "$project" == *"$environment"* ]] ||
    ff_die "project '$project' does not contain environment '$environment'"
}

ff_real_file() {
  local label="$1" value="$2"
  [[ -n "$value" && "$value" = /* ]] || ff_die "$label must be an absolute path"
  [[ -f "$value" && ! -L "$value" ]] || ff_die "$label must be a regular non-symlink file: $value"
  (cd "$(dirname "$value")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "$value")")
}

ff_safe_output_dir() {
  local value="$1"
  [[ -n "$value" && "$value" = /* ]] || ff_die "output/evidence directory must be absolute"
  [[ "$value" != "/" && "$value" != "$HOME" ]] || ff_die "refusing broad output directory: $value"
  [[ "$value" == *frameflow* ]] || ff_die "directory must contain 'frameflow' as an ownership guard"
  if [[ -e "$value" ]]; then
    [[ -d "$value" && ! -L "$value" ]] || ff_die "directory must be a non-symlink directory: $value"
  fi
}

ff_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

ff_write_backup_checksums() {
  local root="$1"
  [[ "$root" = /* && -d "$root" && ! -L "$root" ]] ||
    ff_die "checksum root must be an absolute non-symlink directory: $root"

  # ★ 核心：manifest 必须先落盘，再对备份目录里的完整 regular-file 集合做摘要。
  # 若只给“已知文件”写 checksum，漏掉或额外塞入的 artifact 都可能绕过完整性门禁。
  FRAMEFLOW_CHECKSUM_ROOT="$root" python3 - <<'PY'
import hashlib
import os
import stat
import tempfile
from pathlib import Path

root = Path(os.environ["FRAMEFLOW_CHECKSUM_ROOT"])
manifest = root / "manifest.json"
if manifest.is_symlink() or not manifest.is_file():
    raise SystemExit("manifest.json must exist as a regular non-symlink file before checksums")


def collect_regular_files() -> list[tuple[str, Path]]:
    result: list[tuple[str, Path]] = []
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
                if relative == "SHA256SUMS":
                    continue
                if "\n" in relative or "\r" in relative:
                    raise SystemExit(f"checksum path contains a line break: {relative!r}")
                result.append((relative, path))
    return sorted(result)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(path, flags)
    try:
        info = os.fstat(descriptor)
        if not stat.S_ISREG(info.st_mode):
            raise SystemExit(f"checksum artifact is no longer a regular file: {path}")
        with os.fdopen(descriptor, "rb", closefd=False) as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
    finally:
        os.close(descriptor)
    return digest.hexdigest()


files = collect_regular_files()
if not files:
    raise SystemExit("backup set contains no checksum-eligible files")

output = root / "SHA256SUMS"
descriptor, temporary_name = tempfile.mkstemp(prefix=".frameflow-sha256.", dir=root)
temporary = Path(temporary_name)
try:
    with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
        for relative, path in files:
            stream.write(f"{sha256(path)}  {relative}\n")
    os.chmod(temporary, 0o600)
    os.replace(temporary, output)
finally:
    if temporary.exists():
        temporary.unlink()
PY
}

ff_compose() {
  docker compose --env-file "$FF_ENV_FILE" -p "$FF_PROJECT_NAME" -f "$FF_COMPOSE_FILE" "$@"
}

ff_verify_compose_identity() {
  command -v docker >/dev/null 2>&1 || ff_die "Docker is required for --execute"
  docker compose version >/dev/null 2>&1 || ff_die "Docker Compose v2 is required"
  local rendered
  rendered="$(ff_compose config)" || ff_die "Compose config failed"
  printf '%s\n' "$rendered" | grep -Eq "FRAMEFLOW_ENV: ['\"]?$FF_ENVIRONMENT['\"]?$" ||
    ff_die "Compose config does not declare FRAMEFLOW_ENV=$FF_ENVIRONMENT"
}

ff_verify_service_container() {
  local service="$1" container project_label
  container="$(ff_compose ps -q "$service")"
  [[ -n "$container" && "$(printf '%s\n' "$container" | wc -l | tr -d ' ')" == "1" ]] ||
    ff_die "service '$service' must resolve to exactly one running container"
  project_label="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")"
  [[ "$project_label" == "$FF_PROJECT_NAME" ]] || ff_die "container project label mismatch"
  printf '%s\n' "$container"
}

ff_verify_service_stopped() {
  local service="$1" container project_label running
  container="$(ff_compose ps --all -q "$service")"
  [[ -n "$container" && "$(printf '%s\n' "$container" | wc -l | tr -d ' ')" == "1" ]] ||
    ff_die "service '$service' must resolve to exactly one existing container"
  project_label="$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")"
  [[ "$project_label" == "$FF_PROJECT_NAME" ]] || ff_die "container project label mismatch"
  running="$(docker inspect -f '{{.State.Running}}' "$container")"
  [[ "$running" == false ]] ||
    ff_die "service '$service' must already be stopped for target restore"
  printf '%s\n' "$container"
}

ff_print_plan() {
  printf 'DRY-RUN: '
  printf '%q ' "$@"
  printf '\n'
}

ff_write_json_manifest() {
  # Caller passes KEY=VALUE arguments. Values are treated as strings and never shell-evaluated.
  local output="$1"
  shift
  FRAMEFLOW_MANIFEST_OUTPUT="$output" python3 - "$@" <<'PY'
import json
import os
import sys
from pathlib import Path

document = {}
for item in sys.argv[1:]:
    key, value = item.split("=", 1)
    document[key] = value
Path(os.environ["FRAMEFLOW_MANIFEST_OUTPUT"]).write_text(
    json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
PY
}

ff_read_manifest_field() {
  local manifest="$1" field="$2"
  FRAMEFLOW_MANIFEST="$manifest" FRAMEFLOW_FIELD="$field" python3 - <<'PY'
import json
import os
from pathlib import Path

doc = json.loads(Path(os.environ["FRAMEFLOW_MANIFEST"]).read_text(encoding="utf-8"))
value = doc.get(os.environ["FRAMEFLOW_FIELD"])
if value is None or isinstance(value, (dict, list)):
    raise SystemExit(2)
print(value)
PY
}
