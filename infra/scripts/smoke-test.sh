#!/usr/bin/env bash
set -euo pipefail

environment=""
compose_file=""
env_file=""
base_url=""
media_url=""
image_env_file=""
timeout_seconds=180

usage() {
  echo "usage: $0 --environment ENV --compose-file FILE --env-file FILE --base-url URL [--image-env-file FILE] [--media-url URL] [--timeout SECONDS]" >&2
}

while (($#)); do
  case "$1" in
    --environment) environment=${2:?}; shift 2 ;;
    --compose-file) compose_file=${2:?}; shift 2 ;;
    --env-file) env_file=${2:?}; shift 2 ;;
    --base-url) base_url=${2:?}; shift 2 ;;
    --image-env-file) image_env_file=${2:?}; shift 2 ;;
    --media-url) media_url=${2:?}; shift 2 ;;
    --timeout) timeout_seconds=${2:?}; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done

[[ $environment =~ ^(local|dev|staging|production)$ ]] || { usage; exit 2; }
[[ -f $compose_file && -f $env_file && $base_url =~ ^https?:// ]] || { usage; exit 2; }
[[ $timeout_seconds =~ ^[0-9]+$ ]] || { echo "timeout must be an integer" >&2; exit 2; }
if [[ $environment != local && $base_url != https://* ]]; then
  echo "remote smoke requires an https base URL" >&2
  exit 2
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd -P)
repo_root=$(CDPATH= cd -- "$script_dir/../.." && pwd -P)
"$script_dir/check-env-isolation.sh" --environment "$environment" --env-file "$env_file"
read_env_key() {
  python3 - "$env_file" "$1" <<'PY'
import pathlib, sys
key = sys.argv[2]
for raw in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    line = raw.strip()
    if line and not line.startswith("#") and line.split("=", 1)[0].strip() == key:
        print(line.split("=", 1)[1].strip())
        raise SystemExit(0)
raise SystemExit(f"missing env key: {key}")
PY
}
[[ -n $media_url ]] || media_url=$(read_env_key FRAMEFLOW_STORAGE_PUBLIC_ENDPOINT)
storage_bucket=$(read_env_key FRAMEFLOW_STORAGE_BUCKET)
cors_origin=$(read_env_key FRAMEFLOW_STORAGE_CORS_ORIGINS)

compose=(docker compose --project-name "frameflow-$environment" --env-file "$env_file")
if [[ -n $image_env_file ]]; then
  [[ -f $image_env_file ]] || { echo "image env file not found: $image_env_file" >&2; exit 2; }
  compose+=(--env-file "$image_env_file")
fi
compose+=(-f "$compose_file")
expected=(postgres redis rabbitmq minio app worker web)
deadline=$((SECONDS + timeout_seconds))

# ★ 核心：Smoke 同时验证 Compose 进程、内部依赖和公网入口；只测首页会漏掉
# DB/MQ/Worker 已坏的“绿壳发布”，只测容器又会漏掉 DNS/TLS/Nginx 错配。
while :; do
  running="$("${compose[@]}" ps --status running --services 2>/dev/null || true)"
  missing=()
  for service in "${expected[@]}"; do
    grep -qx "$service" <<<"$running" || missing+=("$service")
  done
  ((${#missing[@]} == 0)) && break
  if ((SECONDS >= deadline)); then
    echo "smoke: FAIL: services not running: ${missing[*]}" >&2
    "${compose[@]}" ps >&2 || true
    exit 1
  fi
  sleep 3
done

"${compose[@]}" exec -T postgres sh -ec 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null
"${compose[@]}" exec -T redis sh -ec 'redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" ping | grep -q PONG'
"${compose[@]}" exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null
"${compose[@]}" exec -T minio mc ready local >/dev/null
"${compose[@]}" exec -T app curl --fail --silent http://127.0.0.1:18080/actuator/health | grep -q '"status":"UP"'
"${compose[@]}" exec -T app curl --fail --silent http://127.0.0.1:18080/api/v1/ping | grep -q 'frameflow'
# Worker 指标线程存活不代表 Rabbit consumer 已注册。必须在 Worker 容器内读取
# readiness gauge，拒绝“metrics 端点 200、实际不消费”的绿壳发布。
"${compose[@]}" exec -T worker python -c '
import urllib.request
import re

payload = urllib.request.urlopen("http://127.0.0.1:9108/metrics", timeout=5).read().decode()
samples = [
    line for line in payload.splitlines()
    if re.match(r"^frameflow_worker_ready(?:\{[^}]*\})?\s+", line)
]
if len(samples) != 1 or float(samples[0].split()[1]) != 1.0:
    raise SystemExit("frameflow_worker_ready is not exactly 1")
'
"${compose[@]}" exec -T web node -e "fetch('http://127.0.0.1:3000/').then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))"

headers=$(mktemp)
body=$(mktemp)
cors_headers=$(mktemp)
trap 'rm -f "$headers" "$body" "$cors_headers"' EXIT
curl --fail --silent --show-error --location --max-time 20 --dump-header "$headers" --output "$body" "$base_url/"
grep -qi 'frameflow' "$body" || { echo "smoke: FAIL: public page is not FrameFlow" >&2; exit 1; }
if [[ $environment != local ]]; then
  grep -qi '^strict-transport-security:' "$headers" || {
    echo "smoke: FAIL: HTTPS response lacks HSTS" >&2; exit 1;
  }
fi
curl --fail --silent --show-error --max-time 20 "$media_url/minio/health/live" >/dev/null
curl --fail --silent --show-error --max-time 20 --request OPTIONS \
  --header "Origin: $cors_origin" \
  --header 'Access-Control-Request-Method: PUT' \
  --header 'Access-Control-Request-Headers: content-type' \
  --dump-header "$cors_headers" --output /dev/null \
  "$media_url/$storage_bucket/frameflow-cors-smoke"
grep -Fqi "Access-Control-Allow-Origin: $cors_origin" "$cors_headers" || {
  echo "smoke: FAIL: MinIO CORS does not allow $cors_origin" >&2
  exit 1
}

echo "smoke: PASS ($environment, $base_url)"
