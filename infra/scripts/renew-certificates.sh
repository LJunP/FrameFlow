#!/usr/bin/env bash
set -euo pipefail

apply=false
confirm=""
while (($#)); do
  case "$1" in
    --apply) apply=true; shift ;;
    --confirm) confirm=${2:?}; shift 2 ;;
    *) echo "usage: $0 [--apply --confirm RENEW_CERTIFICATES]" >&2; exit 2 ;;
  esac
done
if ! $apply; then
  echo "DRY-RUN: would run certbot renew and reload Nginx only after nginx -t"
  exit 0
fi
[[ $confirm == RENEW_CERTIFICATES ]] || { echo "refusing apply" >&2; exit 2; }

# ★ 核心：reload 是 certbot 的 deploy-hook，只在证书真实更新且 nginx -t 通过后执行。
# 若无条件 reload，损坏的配置会把一次无害续期检查升级成站点中断。
certbot renew --quiet --deploy-hook 'nginx -t && systemctl reload nginx'
echo "certificate renewal check: PASS"
