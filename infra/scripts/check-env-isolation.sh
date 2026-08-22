#!/usr/bin/env bash
# F10 之前的骨架检查:确认本机未把生产 Secret 带入本地环境。
# 由项目所有者扩展为真实环境隔离检查(网络/卷/数据分离)。
set -euo pipefail
for env in dev staging production; do
  if [ -d "infra/$env" ] && ls infra/$env/.env >/dev/null 2>&1; then
    echo "WARN: infra/$env/.env exists; ensure it is NOT committed (see .gitignore)"
  fi
done
echo "env-isolation skeleton check: ok"
