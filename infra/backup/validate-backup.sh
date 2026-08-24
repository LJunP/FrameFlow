#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
[[ -f "$SCRIPT_DIR/.f10-backup-root" ]] || { echo "ERROR: backup root sentinel missing" >&2; exit 2; }
for script in "$SCRIPT_DIR"/*.sh "$SCRIPT_DIR"/lib/*.sh "$SCRIPT_DIR"/tests/*.sh; do
  bash -n "$script"
done
bash "$SCRIPT_DIR/tests/test-safety.sh"
grep -q -- '--env-file' "$SCRIPT_DIR/postgres-backup.sh"
grep -q -- '--env-file' "$SCRIPT_DIR/minio-backup.sh"
grep -q 'versionHistoryIncluded=false' "$SCRIPT_DIR/minio-backup.sh"

assert_manifest_precedes_checksums() {
  local script="$1" manifest_line checksum_line
  manifest_line="$(grep -n '^ff_write_json_manifest ' "$script" | tail -n 1 | cut -d: -f1 || true)"
  checksum_line="$(grep -n '^ff_write_backup_checksums ' "$script" | tail -n 1 | cut -d: -f1 || true)"
  [[ -n "$manifest_line" && -n "$checksum_line" && "$manifest_line" -lt "$checksum_line" ]] || {
    echo "ERROR: manifest must be written before complete checksum set: $script" >&2
    exit 1
  }
}
assert_manifest_precedes_checksums "$SCRIPT_DIR/postgres-backup.sh"
assert_manifest_precedes_checksums "$SCRIPT_DIR/minio-backup.sh"
grep -q 'expected_files != actual_files' "$SCRIPT_DIR/verify-backup-set.sh"

assert_quiescence_precedes_mutation() {
  local script="$1" mutation_pattern="$2" last_quiescence mutation_line
  last_quiescence="$(grep -n '^ff_verify_service_stopped ' "$script" | tail -n 1 | cut -d: -f1 || true)"
  mutation_line="$(grep -n "$mutation_pattern" "$script" | head -n 1 | cut -d: -f1 || true)"
  [[ -n "$last_quiescence" && -n "$mutation_line" && "$last_quiescence" -lt "$mutation_line" ]] || {
    echo "ERROR: app/worker/web quiescence must precede target mutation: $script" >&2
    exit 1
  }
  for service in app worker web; do
    grep -Fq "ff_verify_service_stopped $service" "$script" || {
      echo "ERROR: target restore must require stopped $service: $script" >&2
      exit 1
    }
  done
}
assert_quiescence_precedes_mutation "$SCRIPT_DIR/postgres-restore-target.sh" 'dropdb --force'
assert_quiescence_precedes_mutation "$SCRIPT_DIR/minio-restore-target.sh" 'mc mirror --overwrite --remove'

grep -Fq 'User=frameflow' "$SCRIPT_DIR/systemd/frameflow-backup@.service" &&
  grep -Fq 'SupplementaryGroups=docker' "$SCRIPT_DIR/systemd/frameflow-backup@.service" &&
  grep -Fq '/opt/frameflow/ops/current/infra/backup/run-scheduled-backups.sh' \
    "$SCRIPT_DIR/systemd/frameflow-backup@.service" &&
  grep -Fq '/etc/frameflow/%i/backup.conf' "$SCRIPT_DIR/systemd/frameflow-backup@.service" &&
  grep -Fq 'ENV_FILE=/etc/frameflow/production/frameflow.env' \
    "$SCRIPT_DIR/config/backup.conf.example" &&
  grep -Fq "640:root:frameflow" "$SCRIPT_DIR/run-scheduled-backups.sh" &&
  grep -Fq -- '--validate-only' "$SCRIPT_DIR/run-scheduled-backups.sh" || {
    echo "ERROR: systemd backup identity, stable ops path, and config permissions diverged" >&2
    exit 1
  }
echo "F10 backup static validation: PASS"
