#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
repo_root=$(cd "$script_dir/../.." && pwd -P)

if [[ ! -f "$repo_root/AGENTS.md" || ! -f "$repo_root/docs/03-开发与学习路线.md" ]]; then
  echo "FrameFlow repository sentinel missing" >&2
  exit 66
fi

# ★ 核心：解析并校验仓库根后再交给 Python 入口；若依赖调用者当前目录，脚本
# 从其他位置执行时会把合成 Evidence 写进错误工作区。
exec python3 "$repo_root/scripts/pilot/run_synthetic_rehearsal.py" "$@"
