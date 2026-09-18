#!/usr/bin/env bash
# FrameFlow 异步管线压测 —— 一条命令入口。
#
#   bash scripts/loadtest/run-loadtest.sh                # 默认档
#   bash scripts/loadtest/run-loadtest.sh --preflight    # 只核查环境
#   bash scripts/loadtest/run-loadtest.sh --videos 60 --concurrency 8
#
# 凭据只从环境变量读，绝不写入仓库：
#   export FF_EMAIL=you@example.com
#   export FF_PASSWORD=...
#   export FF_ADMIN_KEY=...     # 可选；没有则队列深度与 DLQ 观测降级
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
cd "$REPO_ROOT"

BASE_URL="${FF_BASE_URL:-http://127.0.0.1:18080}"

if ! command -v python3 >/dev/null 2>&1; then
  echo "需要 python3" >&2; exit 1
fi

# 先探一下栈在不在，比让脚本跑一半再超时要快得多
if ! curl -fsS -m 5 "${BASE_URL}/api/v1/ping" >/dev/null 2>&1; then
  cat >&2 <<MSG
无法连到 ${BASE_URL}/api/v1/ping

本地栈没起。先执行（见 README「方式一」）：
  docker compose -f infra/local/docker-compose.yml up -d
  # 等 app 健康后再重跑本脚本
MSG
  exit 1
fi

if [[ -z "${FF_EMAIL:-}" || -z "${FF_PASSWORD:-}" ]]; then
  echo "需要 FF_EMAIL 与 FF_PASSWORD（凭据不写入仓库）" >&2
  exit 1
fi

exec python3 "$SCRIPT_DIR/loadtest.py" --base-url "$BASE_URL" "$@"
