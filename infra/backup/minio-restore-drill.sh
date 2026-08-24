#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
BACKUP_ROOT="$SCRIPT_DIR"
export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP_ROOT"
# shellcheck source=lib/common.sh
source "$BACKUP_ROOT/lib/common.sh"

usage() {
  echo "Usage: $0 --source-environment <env> --backup-dir <absolute path> --evidence-dir <absolute frameflow path> [--minio-image <pinned>] [--mc-image <pinned>] [--execute]" >&2
}

source_environment="" backup_dir="" evidence_dir=""
minio_image="minio/minio:RELEASE.2025-04-22T22-12-26Z"
mc_image="minio/mc:RELEASE.2025-04-16T18-13-26Z"
execute=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --source-environment) source_environment="${2:-}"; shift 2 ;;
    --backup-dir) backup_dir="${2:-}"; shift 2 ;;
    --evidence-dir) evidence_dir="${2:-}"; shift 2 ;;
    --minio-image) minio_image="${2:-}"; shift 2 ;;
    --mc-image) mc_image="${2:-}"; shift 2 ;;
    --execute) execute=true; shift ;;
    *) usage; exit 2 ;;
  esac
done

ff_require_environment "$source_environment"
ff_safe_output_dir "$backup_dir"
ff_safe_output_dir "$evidence_dir"
[[ -d "$backup_dir" && ! -L "$backup_dir" ]] || ff_die "backup directory not found"
[[ "$minio_image" == minio/minio:RELEASE.* ]] || ff_die "pinned MinIO RELEASE image required"
[[ "$mc_image" == minio/mc:RELEASE.* ]] || ff_die "pinned mc RELEASE image required"
manifest="$backup_dir/manifest.json"
[[ "$(ff_read_manifest_field "$manifest" artifactType)" == "minio-current-objects" ]] || ff_die "not a MinIO backup"
[[ "$(ff_read_manifest_field "$manifest" sourceEnvironment)" == "$source_environment" ]] || ff_die "source environment mismatch"

if [[ "$execute" != true ]]; then
  ff_print_plan "$0" --source-environment "$source_environment" --backup-dir "$backup_dir" \
    --evidence-dir "$evidence_dir" --minio-image "$minio_image" --mc-image "$mc_image" --execute
  exit 0
fi

command -v docker >/dev/null 2>&1 || ff_die "Docker is required for restore drill"
docker info >/dev/null 2>&1 || ff_die "Docker daemon is unavailable"
"$BACKUP_ROOT/verify-backup-set.sh" --backup-dir "$backup_dir"

drill_id="$(date -u +%Y%m%dT%H%M%SZ)-$$"
container="frameflow-minio-restore-drill-$drill_id"
network="frameflow-minio-restore-drill-net-$drill_id"
volume="frameflow-minio-restore-drill-data-$drill_id"
verify_dir="$evidence_dir/.tmp-minio-verify-$drill_id"
bucket="$(ff_read_manifest_field "$manifest" bucket)"
ff_require_identifier bucket "$bucket"
for value in "$container" "$network" "$volume"; do
  [[ "$value" == frameflow-minio-restore-drill-* ]] || ff_die "unsafe drill namespace"
done

cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  docker volume rm "$volume" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  if [[ -d "$verify_dir" && "$verify_dir" == "$evidence_dir/.tmp-minio-verify-$drill_id" ]]; then
    find "$verify_dir" -type f -delete
    find "$verify_dir" -depth -type d -empty -delete
  fi
}
trap cleanup EXIT INT TERM

umask 077
mkdir -p "$evidence_dir" "$verify_dir"
docker network create "$network" >/dev/null
docker volume create "$volume" >/dev/null
docker run -d --name "$container" --network "$network" \
  -e MINIO_ROOT_USER=frameflow_restore -e MINIO_ROOT_PASSWORD=frameflow_restore_drill_only \
  -v "$volume:/data" "$minio_image" server /data >/dev/null

ready=false
for _ in $(seq 1 60); do
  if docker run --rm --network "$network" --entrypoint /bin/sh "$mc_image" -ec \
    "mc alias set drill http://$container:9000 frameflow_restore frameflow_restore_drill_only >/dev/null && mc ready drill >/dev/null" \
    >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[[ "$ready" == true ]] || ff_die "isolated MinIO did not become ready"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
docker run --rm --network "$network" --entrypoint /bin/sh \
  -v "$backup_dir/objects:/backup:ro" "$mc_image" -ec \
  "mc alias set drill http://$container:9000 frameflow_restore frameflow_restore_drill_only >/dev/null; mc mb --ignore-existing drill/$bucket; mc mirror --overwrite /backup drill/$bucket"
docker run --rm --network "$network" --entrypoint /bin/sh \
  -v "$verify_dir:/verify" "$mc_image" -ec \
  "mc alias set drill http://$container:9000 frameflow_restore frameflow_restore_drill_only >/dev/null; mc mirror --overwrite drill/$bucket /verify"

FRAMEFLOW_SOURCE_OBJECTS="$backup_dir/objects" FRAMEFLOW_RESTORED_OBJECTS="$verify_dir" python3 - <<'PY'
import hashlib
import os
from pathlib import Path

source = Path(os.environ["FRAMEFLOW_SOURCE_OBJECTS"])
restored = Path(os.environ["FRAMEFLOW_RESTORED_OBJECTS"])
def hashes(root: Path) -> dict[str, str]:
    return {p.relative_to(root).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest()
            for p in root.rglob("*") if p.is_file()}
if hashes(source) != hashes(restored):
    raise SystemExit("restored MinIO bytes differ from backup")
PY
completed_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
evidence="$evidence_dir/minio-restore-drill-$drill_id.json"
ff_write_json_manifest "$evidence" \
  "schemaVersion=1" "drillType=minio-isolated-restore" "result=PASS" \
  "sourceEnvironment=$source_environment" "backupId=$(ff_read_manifest_field "$manifest" backupId)" \
  "bucket=$bucket" "objectCount=$(ff_read_manifest_field "$manifest" objectCount)" \
  "targetNamespace=restore-drill" "targetImage=$minio_image" "hostPortPublished=false" \
  "byteForByteVerified=true" "startedAt=$started_at" "completedAt=$completed_at" \
  "cleanupRequired=true" "versionHistoryIncluded=false"
cleanup
trap - EXIT INT TERM
echo "MinIO isolated restore drill PASS: $evidence (isolated resources removed)"
