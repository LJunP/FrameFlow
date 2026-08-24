#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

[[ $# -eq 5 && "$1" == "--environment" && "$3" == "--config" &&
   "$5" =~ ^(--execute|--validate-only)$ ]] ||
  ff_die "Usage: $0 --environment <env> --config <absolute root:frameflow 0640 path> <--validate-only|--execute>"
environment="$2"
config_file="$(ff_real_file config "$4")"
mode="$5"
ff_require_environment "$environment"
require_systemd_readable_file() {
  local label="$1" path="$2" metadata
  metadata="$(stat -c '%a:%U:%G' "$path" 2>/dev/null || true)"
  [[ "$metadata" == "640:root:frameflow" ]] ||
    ff_die "$label must be owned root:frameflow with mode 0640 (found ${metadata:-unknown})"
}
require_systemd_readable_file config "$config_file"

# 只解析白名单 KEY=VALUE；不 source 配置，避免 root systemd unit 执行任意 shell。
declare -A cfg
while IFS='=' read -r key value; do
  [[ -z "$key" || "$key" == \#* ]] && continue
  case "$key" in
    COMPOSE_FILE|ENV_FILE|PROJECT_NAME|POSTGRES_SERVICE|POSTGRES_DATABASE|POSTGRES_USERNAME|MINIO_CLIENT_SERVICE|MINIO_BUCKET|OUTPUT_DIR)
      cfg["$key"]="$value" ;;
    *) ff_die "unknown scheduled config key: $key" ;;
  esac
done <"$config_file"
for key in COMPOSE_FILE ENV_FILE PROJECT_NAME POSTGRES_SERVICE POSTGRES_DATABASE POSTGRES_USERNAME MINIO_CLIENT_SERVICE MINIO_BUCKET OUTPUT_DIR; do
  [[ -n "${cfg[$key]:-}" ]] || ff_die "scheduled config missing $key"
done
env_file="$(ff_real_file ENV_FILE "${cfg[ENV_FILE]}")"
require_systemd_readable_file ENV_FILE "$env_file"
compose_file="$(ff_real_file COMPOSE_FILE "${cfg[COMPOSE_FILE]}")"
ff_require_project "$environment" "${cfg[PROJECT_NAME]}"
for pair in \
  "POSTGRES_SERVICE:${cfg[POSTGRES_SERVICE]}" \
  "POSTGRES_DATABASE:${cfg[POSTGRES_DATABASE]}" \
  "POSTGRES_USERNAME:${cfg[POSTGRES_USERNAME]}" \
  "MINIO_CLIENT_SERVICE:${cfg[MINIO_CLIENT_SERVICE]}" \
  "MINIO_BUCKET:${cfg[MINIO_BUCKET]}"; do
  ff_require_identifier "${pair%%:*}" "${pair#*:}"
done
ff_safe_output_dir "${cfg[OUTPUT_DIR]}"
[[ -d ${cfg[OUTPUT_DIR]} && ! -L ${cfg[OUTPUT_DIR]} && -w ${cfg[OUTPUT_DIR]} ]] ||
  ff_die "OUTPUT_DIR must be an existing writable non-symlink directory"

if [[ "$mode" == --validate-only ]]; then
  echo "scheduled backup preflight: PASS ($environment, non-root readable config/env, writable output)"
  exit 0
fi

"$BACKUP_ROOT/postgres-backup.sh" --environment "$environment" \
  --compose-file "$compose_file" --env-file "$env_file" \
  --project-name "${cfg[PROJECT_NAME]}" --service "${cfg[POSTGRES_SERVICE]}" \
  --database "${cfg[POSTGRES_DATABASE]}" --username "${cfg[POSTGRES_USERNAME]}" \
  --output-dir "${cfg[OUTPUT_DIR]}" --execute
"$BACKUP_ROOT/minio-backup.sh" --environment "$environment" \
  --compose-file "$compose_file" --env-file "$env_file" \
  --project-name "${cfg[PROJECT_NAME]}" --client-service "${cfg[MINIO_CLIENT_SERVICE]}" \
  --bucket "${cfg[MINIO_BUCKET]}" --output-dir "${cfg[OUTPUT_DIR]}" --execute
