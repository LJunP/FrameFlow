#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage: postgres-backup.sh --environment <env> --compose-file <absolute path> --env-file <absolute path>
  --project-name <frameflow-*> --service <postgres> --database <name>
  --username <name> --output-dir <absolute frameflow path> [--execute]
EOF
}

environment="" compose_file="" env_file="" project="" service="" database="" username="" output_dir=""
execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --service) service="${2:-}"; shift 2 ;;
    --database) database="${2:-}"; shift 2 ;;
    --username) username="${2:-}"; shift 2 ;;
    --output-dir) output_dir="${2:-}"; shift 2 ;;
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
ff_safe_output_dir "$output_dir"

export FF_ENVIRONMENT="$environment" FF_COMPOSE_FILE="$compose_file" FF_ENV_FILE="$env_file" FF_PROJECT_NAME="$project"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_id="postgres-$environment-$timestamp"
final_dir="$output_dir/$backup_id"

if [[ "$execute" != true ]]; then
  ff_print_plan "$0" --environment "$environment" --compose-file "$compose_file" \
    --env-file "$env_file" \
    --project-name "$project" --service "$service" --database "$database" \
    --username "$username" --output-dir "$output_dir" --execute
  exit 0
fi

ff_verify_compose_identity
container="$(ff_verify_service_container "$service")"
[[ ! -e "$final_dir" ]] || ff_die "backup destination already exists: $final_dir"
umask 077
mkdir -p "$output_dir"
staging_dir="$(mktemp -d "$output_dir/.tmp.$backup_id.XXXXXX")"
cleanup() {
  if [[ -d "$staging_dir" && "$staging_dir" == "$output_dir/.tmp.$backup_id."* ]]; then
    find "$staging_dir" -type f -delete
    find "$staging_dir" -depth -type d -empty -delete
  fi
}
trap cleanup EXIT

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
# ★ 核心：custom-format + --no-owner/--no-privileges 让备份可恢复到隔离用户；
# stdout 直接落宿主临时文件，只有 pg_dump 成功并校验后才原子改名。
ff_compose exec -T "$service" pg_dump \
  --username "$username" --dbname "$database" --format=custom \
  --compress=6 --no-owner --no-privileges >"$staging_dir/dump.custom"
[[ -s "$staging_dir/dump.custom" ]] || ff_die "pg_dump produced an empty artifact"
ff_compose exec -T "$service" pg_restore --list <"$staging_dir/dump.custom" >"$staging_dir/restore-list.txt"
object_count="$(grep -Ec '^[0-9]+;' "$staging_dir/restore-list.txt" || true)"
dump_sha="$(ff_sha256 "$staging_dir/dump.custom")"
completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
ff_write_json_manifest "$staging_dir/manifest.json" \
  "schemaVersion=1" "artifactType=postgres-custom-dump" "backupId=$backup_id" \
  "sourceEnvironment=$environment" "sourceProject=$project" "sourceService=$service" \
  "sourceContainer=$container" "database=$database" "databaseUser=$username" \
  "startedAt=$started_at" "completedAt=$completed_at" "dumpSha256=$dump_sha" \
  "restoreListObjectCount=$object_count"
# manifest 也是恢复决策输入，必须在完整文件集 checksum 中，不能只校验 dump。
ff_write_backup_checksums "$staging_dir"
chmod 600 "$staging_dir"/*
mv "$staging_dir" "$final_dir"
trap - EXIT
echo "PostgreSQL backup PASS: $final_dir"
