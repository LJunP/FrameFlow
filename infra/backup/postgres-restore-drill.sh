#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

usage() {
  echo "Usage: $0 --source-environment <env> --backup-dir <absolute path> --evidence-dir <absolute frameflow path> [--postgres-image <image>] [--execute]" >&2
}

source_environment="" backup_dir="" evidence_dir=""
postgres_image="postgres:16.9-alpine3.22"
execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-environment) source_environment="${2:-}"; shift 2 ;;
    --backup-dir) backup_dir="${2:-}"; shift 2 ;;
    --evidence-dir) evidence_dir="${2:-}"; shift 2 ;;
    --postgres-image) postgres_image="${2:-}"; shift 2 ;;
    --execute) execute=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

ff_require_environment "$source_environment"
ff_safe_output_dir "$backup_dir"
ff_safe_output_dir "$evidence_dir"
[[ -d "$backup_dir" && ! -L "$backup_dir" ]] || ff_die "backup directory not found"
[[ "$postgres_image" =~ ^postgres:16[.a-zA-Z0-9_-]*$ ]] || ff_die "restore drill requires a pinned PostgreSQL 16 image"
manifest="$backup_dir/manifest.json"
[[ "$(ff_read_manifest_field "$manifest" artifactType)" == "postgres-custom-dump" ]] || ff_die "not a PostgreSQL backup"
[[ "$(ff_read_manifest_field "$manifest" sourceEnvironment)" == "$source_environment" ]] || ff_die "source environment mismatch"

if [[ "$execute" != true ]]; then
  ff_print_plan "$0" --source-environment "$source_environment" --backup-dir "$backup_dir" \
    --evidence-dir "$evidence_dir" --postgres-image "$postgres_image" --execute
  exit 0
fi

command -v docker >/dev/null 2>&1 || ff_die "Docker is required for restore drill"
docker info >/dev/null 2>&1 || ff_die "Docker daemon is unavailable"
"$BACKUP_ROOT/verify-backup-set.sh" --backup-dir "$backup_dir"

drill_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
container="frameflow-pg-restore-drill-$drill_id"
network="frameflow-pg-restore-drill-net-$drill_id"
volume="frameflow-pg-restore-drill-data-$drill_id"
for value in "$container" "$network" "$volume"; do
  [[ "$value" == frameflow-pg-restore-drill-* ]] || ff_die "unsafe drill namespace"
done

cleanup() {
  # ★ 核心：清理只接受本次随机前缀的精确资源名；即使脚本中断，也不会把
  # 现有 local/dev/prod 容器或卷当成恢复目标删除。
  docker rm -f "$container" >/dev/null 2>&1 || true
  docker volume rm "$volume" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

docker network create "$network" >/dev/null
docker volume create "$volume" >/dev/null
docker run -d --name "$container" --network "$network" \
  -e POSTGRES_DB=frameflow_restore -e POSTGRES_USER=frameflow_restore \
  -e POSTGRES_PASSWORD=frameflow_restore_drill_only \
  -v "$volume:/var/lib/postgresql/data" "$postgres_image" >/dev/null

ready=false
for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -U frameflow_restore -d frameflow_restore >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || ff_die "isolated PostgreSQL did not become ready"
docker cp "$backup_dir/dump.custom" "$container:/tmp/frameflow-restore.custom"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker exec "$container" pg_restore --exit-on-error --no-owner --no-privileges \
  --username frameflow_restore --dbname frameflow_restore /tmp/frameflow-restore.custom
table_count="$(docker exec "$container" psql -U frameflow_restore -d frameflow_restore -Atc \
  "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")"
flyway_count="$(docker exec "$container" psql -U frameflow_restore -d frameflow_restore -Atc \
  "SELECT CASE WHEN to_regclass('public.flyway_schema_history') IS NULL THEN 0 ELSE (SELECT count(*) FROM flyway_schema_history) END;")"
[[ "$table_count" =~ ^[1-9][0-9]*$ ]] || ff_die "restored database has no public tables"
[[ "$flyway_count" =~ ^[1-9][0-9]*$ ]] || ff_die "restored database has no Flyway history"
completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

umask 077
mkdir -p "$evidence_dir"
evidence="$evidence_dir/postgres-restore-drill-$drill_id.json"
ff_write_json_manifest "$evidence" \
  "schemaVersion=1" "drillType=postgres-isolated-restore" "result=PASS" \
  "sourceEnvironment=$source_environment" "backupId=$(ff_read_manifest_field "$manifest" backupId)" \
  "backupSha256=$(ff_read_manifest_field "$manifest" dumpSha256)" \
  "targetNamespace=restore-drill" "targetImage=$postgres_image" \
  "hostPortPublished=false" "publicTableCount=$table_count" "flywayRowCount=$flyway_count" \
  "startedAt=$started_at" "completedAt=$completed_at" "cleanupRequired=true"
cleanup
trap - EXIT INT TERM
echo "PostgreSQL isolated restore drill PASS: $evidence (isolated resources removed)"
