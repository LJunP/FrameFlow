#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage: minio-restore-target.sh --target-environment <env> --compose-file <absolute>
  --env-file <absolute> --project-name <frameflow-*> --client-service <minio-init>
  --bucket <name> --backup-dir <absolute> --pre-restore-backup-dir <absolute frameflow path>
  --confirmation 'RESTORE:<env>:<project>:minio:<bucket>'
  [--maintenance-window-id <id>] [--allow-production] [--execute]
EOF
}

environment="" compose_file="" env_file="" project="" client_service="" bucket=""
backup_dir="" pre_backup_dir="" confirmation="" maintenance_window=""
allow_production=false execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --target-environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --client-service) client_service="${2:-}"; shift 2 ;;
    --bucket) bucket="${2:-}"; shift 2 ;;
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
ff_require_identifier client-service "$client_service"
ff_require_identifier bucket "$bucket"
ff_safe_output_dir "$backup_dir"
ff_safe_output_dir "$pre_backup_dir"
[[ -d "$backup_dir" && ! -L "$backup_dir" ]] || ff_die "backup directory not found"
expected="RESTORE:$environment:$project:minio:$bucket"
[[ "$confirmation" == "$expected" ]] || ff_die "typed confirmation mismatch; expected exactly: $expected"
manifest="$backup_dir/manifest.json"
[[ "$(ff_read_manifest_field "$manifest" artifactType)" == "minio-current-objects" ]] || ff_die "not a MinIO backup"
[[ "$(ff_read_manifest_field "$manifest" sourceEnvironment)" == "$environment" ]] || ff_die "cross-environment target restore is forbidden"
[[ "$(ff_read_manifest_field "$manifest" bucket)" == "$bucket" ]] || ff_die "bucket mismatch"
if [[ "$environment" == production ]]; then
  [[ "$allow_production" == true ]] || ff_die "production restore requires --allow-production"
  ff_require_identifier maintenance-window-id "$maintenance_window"
fi

export FF_ENVIRONMENT="$environment" FF_COMPOSE_FILE="$compose_file" FF_ENV_FILE="$env_file" FF_PROJECT_NAME="$project"
if [[ "$execute" != true ]]; then
  ff_print_plan "$0" --target-environment "$environment" --compose-file "$compose_file" \
    --env-file "$env_file" --project-name "$project" --client-service "$client_service" \
    --bucket "$bucket" --backup-dir "$backup_dir" --pre-restore-backup-dir "$pre_backup_dir" \
    --confirmation "$confirmation" --execute
  exit 0
fi

ff_verify_compose_identity
ff_verify_service_container minio >/dev/null
# App 会签发上传 URL，Worker 会读写对象，Web 仍在线会让用户误以为可操作。
# 三者必须在外部维护窗口中预先停止；脚本不在失败后擅自恢复流量。
ff_verify_service_stopped app >/dev/null
ff_verify_service_stopped worker >/dev/null
ff_verify_service_stopped web >/dev/null
"$BACKUP_ROOT/verify-backup-set.sh" --backup-dir "$backup_dir"

# 与数据库恢复相同：覆盖前先保存目标当前对象，以便操作失败后有可验证回退点。
"$BACKUP_ROOT/minio-backup.sh" --environment "$environment" \
  --compose-file "$compose_file" --env-file "$env_file" --project-name "$project" \
  --client-service "$client_service" --bucket "$bucket" \
  --output-dir "$pre_backup_dir" --execute

# --remove 会让目标与备份精确一致，因此只允许在前置备份、typed confirmation
# 和显式环境全部通过后执行。脚本绝不对另一个 bucket 或环境做通配操作。
ff_compose run --rm --no-deps -T \
  -v "$backup_dir/objects:/restore:ro" --env FRAMEFLOW_EXPECTED_BUCKET="$bucket" \
  --entrypoint /bin/sh "$client_service" -ec '
    mc alias set frameflow http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    test "$FRAMEFLOW_STORAGE_BUCKET" = "$FRAMEFLOW_EXPECTED_BUCKET"
    mc stat "frameflow/$FRAMEFLOW_STORAGE_BUCKET" >/dev/null
    mc mirror --overwrite --remove /restore "frameflow/$FRAMEFLOW_STORAGE_BUCKET"
  '
echo "MinIO target restore PASS: environment=$environment project=$project bucket=$bucket"
