#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage: postgres-restore-target.sh --target-environment <env> --compose-file <absolute path> --env-file <absolute path>
  --project-name <frameflow-*> --service <postgres> --database <name> --username <name>
  --backup-dir <absolute path> --pre-restore-backup-dir <absolute frameflow path>
  --confirmation 'RESTORE:<env>:<project>:<service>:<database>'
  [--maintenance-window-id <id>] [--allow-production] [--execute]
EOF
}

environment="" compose_file="" env_file="" project="" service="" database="" username=""
backup_dir="" pre_backup_dir="" confirmation="" maintenance_window=""
allow_production=false execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --service) service="${2:-}"; shift 2 ;;
    --database) database="${2:-}"; shift 2 ;;
    --username) username="${2:-}"; shift 2 ;;
    --backup-dir) backup_dir="${2:-}"; shift 2 ;;
    --pre-restore-backup-dir) pre_backup_dir="${2:-}"; shift 2 ;;
    --confirmation) confirmation="${2:-}"; shift 2 ;;
    --maintenance-window-id) maintenance_window="${2:-}"; shift 2 ;;
    --allow-production) allow_production=true; shift ;;
    --execute) execute=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

ff_require_environment "$environment"
compose_file="$(ff_real_file compose-file "$compose_file")"
env_file="$(ff_real_file env-file "$env_file")"
ff_require_project "$environment" "$project"
ff_require_identifier service "$service"
ff_require_identifier database "$database"
ff_require_identifier username "$username"
ff_safe_output_dir "$backup_dir"
ff_safe_output_dir "$pre_backup_dir"
[[ -d "$backup_dir" && ! -L "$backup_dir" ]] || ff_die "backup directory not found"
expected="RESTORE:$environment:$project:$service:$database"
[[ "$confirmation" == "$expected" ]] || ff_die "typed confirmation mismatch; expected exactly: $expected"
manifest="$backup_dir/manifest.json"
[[ "$(ff_read_manifest_field "$manifest" artifactType)" == "postgres-custom-dump" ]] || ff_die "not a PostgreSQL backup"
[[ "$(ff_read_manifest_field "$manifest" sourceEnvironment)" == "$environment" ]] ||
  ff_die "cross-environment target restore is forbidden; use isolated restore drill"
if [[ "$environment" == production ]]; then
  [[ "$allow_production" == true ]] || ff_die "production restore requires --allow-production"
  ff_require_identifier maintenance-window-id "$maintenance_window"
fi

export FF_ENVIRONMENT="$environment" FF_COMPOSE_FILE="$compose_file" FF_ENV_FILE="$env_file" FF_PROJECT_NAME="$project"
if [[ "$execute" != true ]]; then
  ff_print_plan "$0" --target-environment "$environment" --compose-file "$compose_file" \
    --env-file "$env_file" \
    --project-name "$project" --service "$service" --database "$database" --username "$username" \
    --backup-dir "$backup_dir" --pre-restore-backup-dir "$pre_backup_dir" \
    --confirmation "$confirmation" --execute
  exit 0
fi

ff_verify_compose_identity
ff_verify_service_container "$service" >/dev/null
# ★ 核心：目标覆盖恢复不替操作者隐藏维护态。App/Worker/Web 必须先由明确的
# 维护窗口停止；任一仍运行就 fail-closed，防止 drop/restore 与新写入竞态。
ff_verify_service_stopped app >/dev/null
ff_verify_service_stopped worker >/dev/null
ff_verify_service_stopped web >/dev/null
"$BACKUP_ROOT/verify-backup-set.sh" --backup-dir "$backup_dir"

# ★ 核心：覆盖恢复前强制再做一次目标现场备份。恢复失败时至少仍有刚刚的
# 可校验回退点；删掉这一步会把一次操作失误升级为不可逆数据丢失。
"$BACKUP_ROOT/postgres-backup.sh" --environment "$environment" \
  --compose-file "$compose_file" --project-name "$project" --service "$service" \
  --env-file "$env_file" \
  --database "$database" --username "$username" --output-dir "$pre_backup_dir" --execute

ff_compose cp "$backup_dir/dump.custom" "$service:/tmp/frameflow-target-restore.custom"
ff_compose exec -T "$service" dropdb --force --username "$username" "$database"
ff_compose exec -T "$service" createdb --username "$username" --owner "$username" "$database"
ff_compose exec -T "$service" pg_restore --exit-on-error --no-owner --no-privileges \
  --username "$username" --dbname "$database" /tmp/frameflow-target-restore.custom
ff_compose exec -T "$service" psql --username "$username" --dbname "$database" \
  -v ON_ERROR_STOP=1 -c "SELECT count(*) AS flyway_rows FROM flyway_schema_history;"
ff_compose exec -T "$service" sh -c 'rm -f /tmp/frameflow-target-restore.custom'
echo "PostgreSQL target restore PASS: environment=$environment project=$project database=$database"
