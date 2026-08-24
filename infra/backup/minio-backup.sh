#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

usage() {
  cat >&2 <<'EOF'
Usage: minio-backup.sh --environment <env> --compose-file <absolute path> --env-file <absolute path>
  --project-name <frameflow-*> --client-service <minio-init> --bucket <name>
  --output-dir <absolute frameflow path> [--execute]
EOF
}

environment="" compose_file="" env_file="" project="" client_service="" bucket="" output_dir=""
execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --environment) environment="${2:-}"; shift 2 ;;
    --compose-file) compose_file="${2:-}"; shift 2 ;;
    --env-file) env_file="${2:-}"; shift 2 ;;
    --project-name) project="${2:-}"; shift 2 ;;
    --client-service) client_service="${2:-}"; shift 2 ;;
    --bucket) bucket="${2:-}"; shift 2 ;;
    --output-dir) output_dir="${2:-}"; shift 2 ;;
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
ff_safe_output_dir "$output_dir"
export FF_ENVIRONMENT="$environment" FF_COMPOSE_FILE="$compose_file" FF_ENV_FILE="$env_file" FF_PROJECT_NAME="$project"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_id="minio-$environment-$timestamp"
final_dir="$output_dir/$backup_id"
if [[ "$execute" != true ]]; then
  ff_print_plan "$0" --environment "$environment" --compose-file "$compose_file" \
    --env-file "$env_file" \
    --project-name "$project" --client-service "$client_service" --bucket "$bucket" \
    --output-dir "$output_dir" --execute
  exit 0
fi

ff_verify_compose_identity
ff_verify_service_container minio >/dev/null
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
mkdir -p "$staging_dir/objects"

started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
# ★ 核心：复用 minio-init 已注入的运行时凭据，Secret 不回读到宿主 shell；
# 目标目录只读写本次随机 staging，成功校验前不会成为正式备份。
ff_compose run --rm --no-deps -T \
  -v "$staging_dir:/backup" --env FRAMEFLOW_EXPECTED_BUCKET="$bucket" \
  --entrypoint /bin/sh "$client_service" -ec '
    mc alias set frameflow http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
    test "$FRAMEFLOW_STORAGE_BUCKET" = "$FRAMEFLOW_EXPECTED_BUCKET"
    mc stat "frameflow/$FRAMEFLOW_STORAGE_BUCKET" >/dev/null
    mc mirror --preserve --overwrite "frameflow/$FRAMEFLOW_STORAGE_BUCKET" /backup/objects
    mc ls --recursive --json "frameflow/$FRAMEFLOW_STORAGE_BUCKET" > /backup/object-inventory.jsonl
  '

object_count="$(find "$staging_dir/objects" -type f | wc -l | tr -d ' ')"
inventory_sha="$(ff_sha256 "$staging_dir/object-inventory.jsonl")"
completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
ff_write_json_manifest "$staging_dir/manifest.json" \
  "schemaVersion=1" "artifactType=minio-current-objects" "backupId=$backup_id" \
  "sourceEnvironment=$environment" "sourceProject=$project" "sourceService=minio" \
  "bucket=$bucket" "objectCount=$object_count" "inventorySha256=$inventory_sha" \
  "versionHistoryIncluded=false" "startedAt=$started_at" "completedAt=$completed_at"
# 先固定 manifest，再覆盖 manifest、inventory 与 objects 下的全部 regular files。
ff_write_backup_checksums "$staging_dir"
chmod -R u=rwX,go= "$staging_dir"
mv "$staging_dir" "$final_dir"
trap - EXIT
echo "MinIO current-object backup PASS: $final_dir"
