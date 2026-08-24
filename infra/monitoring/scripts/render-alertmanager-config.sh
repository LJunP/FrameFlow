#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
MONITORING_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
[[ -f "$MONITORING_ROOT/.f10-monitoring-root" ]] || {
  echo "ERROR: monitoring root sentinel missing" >&2
  exit 2
}

usage() {
  echo "Usage: sudo $0 --environment <dev|staging|production> --secret-file <0600 file> --output <absolute path>" >&2
}

file_mode() {
  case "$(uname -s)" in
    Darwin) stat -f '%Lp' "$1" ;;
    Linux) stat -c '%a' "$1" ;;
    *) echo "ERROR: unsupported stat platform" >&2; return 2 ;;
  esac
}

numeric_owner() {
  case "$(uname -s)" in
    Darwin) stat -f '%u:%g' "$1" ;;
    Linux) stat -c '%u:%g' "$1" ;;
    *) echo "ERROR: unsupported stat platform" >&2; return 2 ;;
  esac
}

environment=""
secret_file=""
output=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) environment="${2:-}"; shift 2 ;;
    --secret-file) secret_file="${2:-}"; shift 2 ;;
    --output) output="${2:-}"; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$environment" =~ ^(dev|staging|production)$ ]] || { echo "ERROR: explicit remote environment required" >&2; exit 2; }
[[ "$EUID" -eq 0 ]] || {
  echo "ERROR: rendering the Alertmanager runtime config requires root" >&2
  exit 2
}
[[ -f "$secret_file" && ! -L "$secret_file" ]] || { echo "ERROR: secret file must be a regular non-symlink file" >&2; exit 2; }
permissions="$(file_mode "$secret_file")"
[[ "$permissions" == "600" ]] || { echo "ERROR: secret file permissions must be 0600" >&2; exit 2; }
[[ -n "$output" && "$output" = /* ]] || { echo "ERROR: output must be an explicit absolute path" >&2; exit 2; }
[[ ! -e "$output" && ! -L "$output" ]] || { echo "ERROR: refusing to overwrite existing output: $output" >&2; exit 2; }

webhook_url="$(head -n 1 "$secret_file")"
[[ "$webhook_url" == https://* ]] || { echo "ERROR: webhook URL must use https" >&2; exit 2; }
umask 077
mkdir -p "$(dirname "$output")"
FRAMEFLOW_TEMPLATE="$MONITORING_ROOT/alertmanager/alertmanager.production.template.yml" \
FRAMEFLOW_OUTPUT="$output" FRAMEFLOW_WEBHOOK_URL="$webhook_url" \
python3 - <<'PY'
import os
from pathlib import Path

template = Path(os.environ["FRAMEFLOW_TEMPLATE"]).read_text(encoding="utf-8")
url = os.environ["FRAMEFLOW_WEBHOOK_URL"]
if "\n" in url or "\r" in url:
    raise SystemExit("webhook URL contains a newline")
flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
if hasattr(os, "O_NOFOLLOW"):
    flags |= os.O_NOFOLLOW
fd = os.open(os.environ["FRAMEFLOW_OUTPUT"], flags, 0o600)
with os.fdopen(fd, "w", encoding="utf-8") as output:
    output.write(template.replace("__FRAMEFLOW_ALERT_WEBHOOK_URL__", url))
PY
# ★ 核心：输入 Secret 只供 root 读取；渲染后的配置则只交给 Alertmanager
# 容器的固定 numeric identity 65534:65534 读取。若仍沿用宿主操作者的 0600，
# nobody 容器会在启动时因 EACCES 失败；若放宽为 group/world-readable，又会泄露 webhook。
chmod 0400 "$output"
chown 65534:65534 "$output"
runtime_permissions="$(file_mode "$output")"
runtime_owner="$(numeric_owner "$output")"
[[ "$runtime_permissions" == "400" && "$runtime_owner" == "65534:65534" ]] || {
  echo "ERROR: rendered config ownership or permissions are unsafe" >&2
  exit 2
}
echo "Rendered Alertmanager config for $environment at $output (secret not printed; owner=65534:65534 mode=0400)."
