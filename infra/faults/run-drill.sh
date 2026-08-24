#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
FAULT_ROOT="$SCRIPT_DIR"
[[ -f "$FAULT_ROOT/.f10-fault-root" ]] || { echo "ERROR: fault root sentinel missing" >&2; exit 2; }

die() { echo "ERROR: $*" >&2; exit 2; }
usage() {
  cat >&2 <<'EOF'
Usage: run-drill.sh --scenario <redis-unavailable|db-unavailable|worker-crash|mq-backlog>
  --environment <local|dev|staging> --compose-file <absolute> --env-file <absolute>
  --project-name <frameflow-*> --health-url <http://127.0.0.1:port/actuator/health>
  --evidence-dir <absolute frameflow path> [--duration-seconds 1..45]
  [--backlog-messages 1..1000] [--allow-staging --confirmation <typed>] [--execute]
EOF
}

scenario="" environment="" compose_file="" env_file="" project="" health_url="" evidence_dir=""
duration=10 backlog_messages=120 allow_staging=false confirmation="" execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --scenario) scenario="${2:-}"; shift 2 ;;
    --environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --health-url) health_url="${2:-}"; shift 2 ;;
    --evidence-dir) evidence_dir="${2:-}"; shift 2 ;;
    --duration-seconds) duration="${2:-}"; shift 2 ;;
    --backlog-messages) backlog_messages="${2:-}"; shift 2 ;;
    --allow-staging) allow_staging=true; shift ;;
    --confirmation) confirmation="${2:-}"; shift 2 ;;
    --execute) execute=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$scenario" =~ ^(redis-unavailable|db-unavailable|worker-crash|mq-backlog)$ ]] || die "unsupported scenario"
[[ "$environment" =~ ^(local|dev|staging)$ ]] || die "production and unknown environments are refused"
[[ "$compose_file" = /* && -f "$compose_file" && ! -L "$compose_file" ]] || die "compose-file must be an absolute regular file"
[[ "$env_file" = /* && -f "$env_file" && ! -L "$env_file" ]] || die "env-file must be an absolute regular file"
[[ "$project" =~ ^frameflow-[a-z0-9-]+$ && "$project" == *"$environment"* ]] || die "project/environment mismatch"
[[ "$health_url" =~ ^http://127\.0\.0\.1:[0-9]{2,5}/actuator/health$ ]] || die "health-url must be an explicit loopback actuator URL"
[[ "$evidence_dir" = /* && "$evidence_dir" == *frameflow* && "$evidence_dir" != "/" && "$evidence_dir" != "$HOME" ]] || die "unsafe evidence directory"
[[ "$duration" =~ ^[0-9]+$ && "$duration" -ge 1 && "$duration" -le 45 ]] || die "duration must be 1..45 seconds"
[[ "$backlog_messages" =~ ^[0-9]+$ && "$backlog_messages" -ge 1 && "$backlog_messages" -le 1000 ]] || die "backlog messages must be 1..1000"
if [[ "$environment" == staging ]]; then
  expected="DRILL:staging:$project:$scenario"
  [[ "$allow_staging" == true && "$confirmation" == "$expected" ]] || die "staging gate requires --allow-staging and $expected"
fi

compose=(docker compose --env-file "$env_file" -p "$project" -f "$compose_file")
if [[ "$execute" != true ]]; then
  printf 'DRY-RUN: '
  printf '%q ' "$0" --scenario "$scenario" --environment "$environment" \
    --compose-file "$compose_file" --env-file "$env_file" --project-name "$project" \
    --health-url "$health_url" --evidence-dir "$evidence_dir" \
    --duration-seconds "$duration" --backlog-messages "$backlog_messages" --execute
  printf '\n'
  exit 0
fi

command -v docker >/dev/null 2>&1 || die "Docker is required"
command -v curl >/dev/null 2>&1 || die "curl is required"
"${compose[@]}" config --quiet
rendered="$("${compose[@]}" config)"
printf '%s\n' "$rendered" | grep -Eq "FRAMEFLOW_ENV: ['\"]?$environment['\"]?$" || die "Compose environment mismatch"

service=""
case "$scenario" in
  redis-unavailable) service=redis ;;
  db-unavailable) service=postgres ;;
  worker-crash) service=worker ;;
  mq-backlog) service=rabbitmq ;;
esac
container="$("${compose[@]}" ps -q "$service")"
[[ -n "$container" && "$(printf '%s\n' "$container" | wc -l | tr -d ' ')" == 1 ]] || die "scenario service must resolve to one running container"
[[ "$(docker inspect -f '{{ index .Config.Labels "com.docker.compose.project" }}' "$container")" == "$project" ]] || die "container project label mismatch"
[[ "$(docker inspect -f '{{.State.Running}}' "$container")" == true ]] || die "scenario service must be running before drill"

wait_for() {
  local description="$1"
  shift
  local attempt
  for attempt in $(seq 1 60); do
    if "$@" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  die "$description did not recover within 60 seconds"
}

app_is_healthy() {
  [[ "$(curl -sS -o /dev/null -w '%{http_code}' --max-time 4 "$health_url" || true)" == 200 ]]
}

postgres_is_ready() {
  "${compose[@]}" exec -T postgres sh -ec \
    'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
}

redis_is_ready() {
  "${compose[@]}" exec -T redis sh -ec \
    'redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" ping | grep -qx PONG'
}

rabbitmq_is_ready() {
  "${compose[@]}" exec -T rabbitmq rabbitmq-diagnostics -q ping
}

worker_is_ready() {
  # app 镜像有 curl，且与 worker 同处 backend 网络；不为 Worker 镜像增加调试工具。
  "${compose[@]}" exec -T -e FRAMEFLOW_EXPECTED_ENV="$environment" app sh -ec '
    curl -fsS http://worker:9108/metrics |
      grep -Eq "^frameflow_worker_ready\\{environment=\\\"${FRAMEFLOW_EXPECTED_ENV}\\\"\\} 1(\\.0)?$"
  '
}

verify_recovery() {
  # ★ 核心：Running 只表示容器进程存在，不表示依赖、业务入口或消费者已恢复。
  # HARNESS_PASS 前必须把四个依赖、API 与 Worker 消费 readiness 全部验证为可用。
  wait_for "PostgreSQL readiness" postgres_is_ready
  wait_for "Redis readiness" redis_is_ready
  wait_for "RabbitMQ readiness" rabbitmq_is_ready
  wait_for "application health" app_is_healthy
  wait_for "worker consumer readiness" worker_is_ready
}

# 故障注入前先证明整栈基线可用，否则演练结果无法归因。
verify_recovery

drill_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
queue="frameflow.drill.$environment.$drill_id"
stopped=false queue_created=false cleanup_done=false
cleanup_best_effort() {
  # pre/during/post 仅是计算证据的临时响应；失败路径也不能在 Evidence 根留下
  # 看似正式但未签发 HARNESS_PASS 的碎片。
  if [[ -d "$evidence_dir" && ! -L "$evidence_dir" ]]; then
    find "$evidence_dir" -maxdepth 1 -type f \
      \( -name ".pre-$drill_id.json" -o -name ".during-$drill_id.json" -o -name ".post-$drill_id.json" \) \
      -delete
  fi
  if [[ "$queue_created" == true ]]; then
    "${compose[@]}" exec -T -e FRAMEFLOW_DRILL_QUEUE="$queue" rabbitmq sh -ec '
      rabbitmqadmin --vhost "$RABBITMQ_DEFAULT_VHOST" --username "$RABBITMQ_DEFAULT_USER" --password "$RABBITMQ_DEFAULT_PASS" --non-interactive --quiet delete queue --name "$FRAMEFLOW_DRILL_QUEUE" --idempotently >/dev/null 2>&1 || true
    ' >/dev/null 2>&1 || true
    queue_created=false
  fi
  if [[ "$stopped" == true ]]; then
    "${compose[@]}" start "$service" >/dev/null 2>&1 || true
    stopped=false
  fi
  cleanup_done=true
}
trap cleanup_best_effort EXIT INT TERM

delete_drill_queue_strict() {
  [[ "$queue_created" == true ]] || return 0
  "${compose[@]}" exec -T -e FRAMEFLOW_DRILL_QUEUE="$queue" rabbitmq sh -ec '
    rabbitmqadmin --vhost "$RABBITMQ_DEFAULT_VHOST" --username "$RABBITMQ_DEFAULT_USER" --password "$RABBITMQ_DEFAULT_PASS" --non-interactive --quiet delete queue --name "$FRAMEFLOW_DRILL_QUEUE" >/dev/null
    if rabbitmqctl -q list_queues --vhost "$RABBITMQ_DEFAULT_VHOST" --no-table-headers name |
      grep -Fx "$FRAMEFLOW_DRILL_QUEUE"; then
      echo "drill queue still exists after delete" >&2
      exit 1
    fi
  '
  queue_created=false
}

umask 077
mkdir -p "$evidence_dir"
evidence="$evidence_dir/$scenario-$drill_id.md"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
image_ref="$(docker inspect -f '{{.Config.Image}}' "$container")"
image_id="$(docker inspect -f '{{.Image}}' "$container")"
pre_health_code="$(curl -sS -o "$evidence_dir/.pre-$drill_id.json" -w '%{http_code}' --max-time 4 "$health_url" || true)"
[[ "$pre_health_code" == 200 ]] || die "baseline application health was not HTTP 200"

if [[ "$scenario" == mq-backlog ]]; then
  "${compose[@]}" exec -T -e FRAMEFLOW_DRILL_QUEUE="$queue" rabbitmq sh -ec '
    # RabbitMQ 4.3 bundles rabbitmqadmin v2: resource fields are explicit flags,
    # not the legacy name=value arguments from rabbitmqadmin v1.
    # RabbitMQ 4.3 默认拒绝已弃用的 transient non-exclusive queue。随机 durable
    # 队列在正常路径被严格删除；x-expires 又为 SIGKILL 等无法执行 trap 的情形设上限。
    rabbitmqadmin --vhost "$RABBITMQ_DEFAULT_VHOST" --username "$RABBITMQ_DEFAULT_USER" --password "$RABBITMQ_DEFAULT_PASS" --non-interactive --quiet declare queue --name "$FRAMEFLOW_DRILL_QUEUE" --durable true --auto-delete false --arguments "{\"x-expires\":600000}" >/dev/null
  '
  queue_created=true
  "${compose[@]}" exec -T -e FRAMEFLOW_DRILL_QUEUE="$queue" -e FRAMEFLOW_DRILL_COUNT="$backlog_messages" rabbitmq sh -ec '
    i=0
    while [ "$i" -lt "$FRAMEFLOW_DRILL_COUNT" ]; do
      rabbitmqadmin --vhost "$RABBITMQ_DEFAULT_VHOST" --username "$RABBITMQ_DEFAULT_USER" --password "$RABBITMQ_DEFAULT_PASS" --non-interactive --quiet publish message --exchange amq.default --routing-key "$FRAMEFLOW_DRILL_QUEUE" --payload "{\"drill\":true}" >/dev/null
      i=$((i + 1))
    done
  '
  sleep "$duration"
  during_observation="$("${compose[@]}" exec -T -e FRAMEFLOW_DRILL_QUEUE="$queue" rabbitmq sh -ec '
    rabbitmqctl -q list_queues --vhost "$RABBITMQ_DEFAULT_VHOST" --no-table-headers name messages |
      awk -v queue="$FRAMEFLOW_DRILL_QUEUE" '\''$1 == queue { print $0 }'\''
  ')"
  observed_count="$(printf '%s\n' "$during_observation" | awk -v queue="$queue" '$1 == queue { print $2 }')"
  [[ "$observed_count" == "$backlog_messages" ]] || die "isolated backlog count mismatch"
else
  "${compose[@]}" stop -t 10 "$service" >/dev/null
  stopped=true
  [[ "$(docker inspect -f '{{.State.Running}}' "$container")" == false ]] || die "fault injection did not stop service"
  during_health_code="$(curl -sS -o "$evidence_dir/.during-$drill_id.json" -w '%{http_code}' --max-time 4 "$health_url" || true)"
  during_observation="service_running=false health_http=$during_health_code"
  sleep "$duration"
  "${compose[@]}" start "$service" >/dev/null
  stopped=false
fi

restored=false
for _ in $(seq 1 45); do
  if [[ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true)" == true ]]; then
    restored=true
    break
  fi
  sleep 1
done
[[ "$restored" == true ]] || die "service did not return to running"
if [[ "$scenario" == mq-backlog ]]; then
  delete_drill_queue_strict
fi
verify_recovery
post_health_code="$(curl -sS -o "$evidence_dir/.post-$drill_id.json" -w '%{http_code}' --max-time 4 "$health_url" || true)"
[[ "$post_health_code" == 200 ]] || die "post-drill application health was not HTTP 200"
completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

{
  echo "# F10 fault drill evidence"
  echo
  echo "- result: HARNESS_PASS"
  echo "- scenario: $scenario"
  echo "- environment: $environment"
  echo "- composeProject: $project"
  echo "- service: $service"
  echo "- containerId: $container"
  echo "- imageRef: $image_ref"
  echo "- imageId: $image_id"
  echo "- startedAt: $started_at"
  echo "- faultObserved: $during_observation"
  echo "- preHealthHttp: $pre_health_code"
  echo "- postHealthHttp: $post_health_code"
  echo "- restoredRunning: true"
  echo "- recoveryChecks: postgres,redis,rabbitmq,app,worker"
  echo "- cleanupVerified: true"
  echo "- cleanupRequired: false"
  if [[ "$scenario" == mq-backlog ]]; then
    echo "- alertBoundary: isolated drill queue; does not trigger the business queue backlog alert"
  fi
  if [[ "$scenario" == redis-unavailable ]]; then
    echo "- monitoringAclBoundary: Redis runtime ACL may be lost after restart; reprovision exporter identity before monitoring resumes"
  fi
  echo "- completedAt: $completed_at"
  echo
  echo "HARNESS_PASS 只证明本地注入/恢复/清理机制，不证明 production 韧性或 RTO。"
  echo "复盘模板：docs/templates/F10-事故复盘模板.md"
} >"$evidence"

find "$evidence_dir" -maxdepth 1 -type f -name ".*-$drill_id.json" -delete
cleanup_done=true
trap - EXIT INT TERM
echo "F10 fault drill HARNESS_PASS: $evidence (cleanup completed)"
