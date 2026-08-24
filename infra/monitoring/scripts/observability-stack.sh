#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
MONITORING_ROOT="$(cd "$SCRIPT_DIR/.." && pwd -P)"
[[ -f "$MONITORING_ROOT/.f10-monitoring-root" ]] || { echo "ERROR: monitoring root sentinel missing" >&2; exit 2; }

usage() {
  cat >&2 <<'EOF'
Usage: observability-stack.sh --action <up|stop|config> --environment <env>
  --compose-file <absolute path> --env-file <absolute path>
  --exporter-env-file <absolute 0600 path> --project-name <frameflow-*>
  --alert-mode <synthetic|external> [--alertmanager-config <absolute 0400 path owned by 65534:65534>]
  [--execute]
EOF
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

action="" environment="" compose_file="" env_file="" exporter_env="" project=""
alert_mode="" alert_config="" execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --action) action="${2:-}"; shift 2 ;;
    --environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --exporter-env-file) exporter_env="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --alert-mode) alert_mode="${2:-}"; shift 2 ;;
    --alertmanager-config) alert_config="${2:-}"; shift 2 ;;
    --execute) execute=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

[[ "$action" =~ ^(up|stop|config)$ ]] || { echo "ERROR: action must be up, stop, or config" >&2; exit 2; }
[[ "$environment" =~ ^(local|dev|staging|production)$ ]] || { echo "ERROR: invalid environment" >&2; exit 2; }
[[ "$project" =~ ^frameflow-[a-z0-9-]+$ && "$project" == *"$environment"* ]] || { echo "ERROR: project/environment mismatch" >&2; exit 2; }
for file in "$compose_file" "$env_file" "$exporter_env"; do
  [[ "$file" = /* && -f "$file" && ! -L "$file" ]] || { echo "ERROR: inputs must be absolute regular non-symlink files: $file" >&2; exit 2; }
done
exporter_permissions="$(file_mode "$exporter_env")"
[[ "$exporter_permissions" == "600" ]] || { echo "ERROR: exporter env file permissions must be 0600" >&2; exit 2; }
[[ "$alert_mode" =~ ^(synthetic|external)$ ]] || { echo "ERROR: alert-mode must be synthetic or external" >&2; exit 2; }
if [[ "$alert_mode" == synthetic ]]; then
  [[ "$environment" != production ]] || { echo "ERROR: production refuses synthetic alert sink" >&2; exit 2; }
  alert_config="$MONITORING_ROOT/alertmanager/alertmanager.local.yml"
else
  [[ "$alert_config" = /* && -f "$alert_config" && ! -L "$alert_config" ]] || { echo "ERROR: external Alertmanager config must be an absolute regular file" >&2; exit 2; }
  [[ "$alert_config" != "$MONITORING_ROOT/alertmanager/alertmanager.local.yml" ]] || { echo "ERROR: local sink config is not an external channel" >&2; exit 2; }
  permissions="$(file_mode "$alert_config")"
  owner="$(numeric_owner "$alert_config")"
  # ★ 核心：外部配置内含 webhook Secret，但读取者是容器内 nobody，而非宿主
  # 操作者。启动前同时锁定 numeric owner 与只读 mode，避免“宿主 0600 安全、
  # 容器却不可读”的假安全，也拒绝用 0444 绕过权限问题。
  [[ "$permissions" == "400" && "$owner" == "65534:65534" ]] || {
    echo "ERROR: external Alertmanager config must be owned by 65534:65534 with permissions 0400" >&2
    exit 2
  }
fi

export FRAMEFLOW_ENV="$environment"
export COMPOSE_PROJECT_NAME="$project"
export FRAMEFLOW_MONITORING_ROOT="$MONITORING_ROOT"
export FRAMEFLOW_ALERTMANAGER_CONFIG="$alert_config"
compose=(docker compose --env-file "$env_file" --env-file "$exporter_env" \
  -p "$project" -f "$compose_file" -f "$MONITORING_ROOT/docker-compose.observability.yml")
base_compose=(docker compose --env-file "$env_file" -p "$project" -f "$compose_file")
if [[ "$alert_mode" == synthetic ]]; then
  compose+=(--profile synthetic-alert-drill)
fi

if [[ "$execute" != true ]]; then
  printf 'DRY-RUN: FRAMEFLOW_ENV=%q COMPOSE_PROJECT_NAME=%q ' "$environment" "$project"
  printf '%q ' "${compose[@]}" "$action"
  printf '\n'
  exit 0
fi

command -v docker >/dev/null 2>&1 || { echo "ERROR: Docker is required" >&2; exit 2; }
"${compose[@]}" config --quiet
if [[ "$action" == up ]]; then
  # ★ 核心：Redis runtime ACL 不写入 RDB/AOF，容器重启可能丢失 monitor 用户。
  # 每次 observability up 都检查两个最小权限 principal 与 RabbitMQ vhost；
  # 缺失就 fail-closed，要求重新运行幂等 provision，不能退回业务管理员凭据。
  "${base_compose[@]}" exec -T postgres sh -ec \
    'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT NOT rolsuper AND rolcanlogin AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AND NOT rolbypassrls AND rolinherit FROM pg_roles WHERE rolname='\''frameflow_monitor'\'';" | grep -qx t
     psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc "SELECT count(*) = 1 AND bool_and(parent.rolname = '\''pg_monitor'\'' AND NOT membership.admin_option) FROM pg_auth_members membership JOIN pg_roles parent ON parent.oid = membership.roleid JOIN pg_roles member_role ON member_role.oid = membership.member WHERE member_role.rolname = '\''frameflow_monitor'\'';" | grep -qx t' || {
      echo "ERROR: PostgreSQL frameflow_monitor missing; rerun provision-exporter-credentials.sh" >&2
      exit 2
    }
  "${base_compose[@]}" exec -T rabbitmq sh -ec '
    rabbitmqctl list_vhosts -q | grep -Fx "$RABBITMQ_DEFAULT_VHOST"
    rabbitmq-plugins list -e -m | grep -Fx rabbitmq_prometheus
  ' || {
      echo "ERROR: RabbitMQ target vhost/plugin is missing; rerun base environment provisioning" >&2
      exit 2
    }
  "${base_compose[@]}" exec -T redis sh -ec '
    redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" ACL GETUSER frameflow_monitor | grep -qx on
    [ "$(redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" --raw ACL DRYRUN frameflow_monitor INFO)" = OK ]
    [ "$(redis-cli --no-auth-warning -a "$FRAMEFLOW_REDIS_PASSWORD" --raw ACL DRYRUN frameflow_monitor GET frameflow:acl-probe 2>&1 || true)" != OK ]
  ' || {
      echo "ERROR: Redis frameflow_monitor missing or ACL boundary invalid; rerun provision" >&2
      exit 2
    }
fi
case "$action" in
  up) "${compose[@]}" up -d ;;
  stop) "${compose[@]}" stop prometheus alertmanager grafana loki alloy \
          postgres-exporter redis-exporter alert-sink ;;
  config) "${compose[@]}" config --services ;;
esac
