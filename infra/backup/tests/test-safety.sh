#!/usr/bin/env bash
set -euo pipefail

TEST_DIR="$(mktemp -d /tmp/frameflow-f10-backup-safety.XXXXXX)"
cleanup() {
  find "$TEST_DIR" -type l -delete
  find "$TEST_DIR" -type f -delete
  find "$TEST_DIR" -depth -type d -empty -delete
}
trap cleanup EXIT
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
BACKUP="$ROOT/infra/backup"
compose="$TEST_DIR/docker-compose.yml"
env_file="$TEST_DIR/frameflow.env"
mkdir -p "$TEST_DIR/frameflow-output" "$TEST_DIR/frameflow-backup"
printf 'services:\n  postgres:\n    image: postgres:16\n    environment:\n      FRAMEFLOW_ENV: local\n' >"$compose"
printf 'FRAMEFLOW_ENV=local\n' >"$env_file"

expect_fail() {
  if "$@" >/dev/null 2>&1; then
    echo "expected failure but command passed: $*" >&2
    exit 1
  fi
}

export FRAMEFLOW_BACKUP_LIB_ROOT="$BACKUP"
# shellcheck source=../lib/common.sh
source "$BACKUP/lib/common.sh"

make_postgres_backup_set() {
  local target="$1"
  mkdir -p "$target"
  printf 'postgres custom dump\n' >"$target/dump.custom"
  printf '1; restore list\n' >"$target/restore-list.txt"
  printf '{"artifactType":"postgres-custom-dump","sourceEnvironment":"local"}\n' \
    >"$target/manifest.json"
  ff_write_backup_checksums "$target"
}

make_minio_backup_set() {
  local target="$1"
  mkdir -p "$target/objects/nested"
  printf 'object inventory\n' >"$target/object-inventory.jsonl"
  printf 'first object\n' >"$target/objects/first.txt"
  printf 'nested object\n' >"$target/objects/nested/second.bin"
  printf '{"artifactType":"minio-current-objects","sourceEnvironment":"local"}\n' \
    >"$target/manifest.json"
  ff_write_backup_checksums "$target"
}

# 无 --execute 时不得调用 Docker，也不得创建正式备份目录。
PATH="/usr/bin:/bin" "$BACKUP/postgres-backup.sh" --environment local \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
  --service postgres --database frameflow --username frameflow \
  --output-dir "$TEST_DIR/frameflow-output" >/dev/null
[[ -z "$(find "$TEST_DIR/frameflow-output" -mindepth 1 -print -quit)" ]] || {
  echo "dry-run unexpectedly wrote backup data" >&2
  exit 1
}

PATH="/usr/bin:/bin" "$BACKUP/minio-backup.sh" --environment local \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
  --client-service minio-init --bucket frameflow-media-local \
  --output-dir "$TEST_DIR/frameflow-output" >/dev/null

expect_fail "$BACKUP/postgres-backup.sh" --environment production \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
  --service postgres --database frameflow --username frameflow \
  --output-dir "$TEST_DIR/frameflow-output"
expect_fail "$BACKUP/postgres-backup.sh" --environment local \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
  --service postgres --database frameflow --username frameflow --output-dir /

# 覆盖恢复必须在读取/执行备份前通过逐字确认；错误确认 fail-closed。
printf '{"artifactType":"postgres-custom-dump","sourceEnvironment":"local"}\n' \
  >"$TEST_DIR/frameflow-backup/manifest.json"
expect_fail "$BACKUP/postgres-restore-target.sh" --target-environment local \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
  --service postgres --database frameflow --username frameflow \
  --backup-dir "$TEST_DIR/frameflow-backup" --pre-restore-backup-dir "$TEST_DIR/frameflow-output" \
  --confirmation WRONG

# 完整性门禁要求 manifest 与实际 regular-file 全集都在 checksum 中。
pg_valid="$TEST_DIR/frameflow-pg-valid"
make_postgres_backup_set "$pg_valid"
"$BACKUP/verify-backup-set.sh" --backup-dir "$pg_valid" >/dev/null
grep -Eq '^[0-9a-f]{64}  dump\.custom$' "$pg_valid/SHA256SUMS"
grep -Eq '^[0-9a-f]{64}  restore-list\.txt$' "$pg_valid/SHA256SUMS"
grep -Eq '^[0-9a-f]{64}  manifest\.json$' "$pg_valid/SHA256SUMS"

minio_valid="$TEST_DIR/frameflow-minio-valid"
make_minio_backup_set "$minio_valid"
"$BACKUP/verify-backup-set.sh" --backup-dir "$minio_valid" >/dev/null
grep -Eq '^[0-9a-f]{64}  manifest\.json$' "$minio_valid/SHA256SUMS"
grep -Eq '^[0-9a-f]{64}  object-inventory\.jsonl$' "$minio_valid/SHA256SUMS"
grep -Eq '^[0-9a-f]{64}  objects/first\.txt$' "$minio_valid/SHA256SUMS"
grep -Eq '^[0-9a-f]{64}  objects/nested/second\.bin$' "$minio_valid/SHA256SUMS"

pg_empty="$TEST_DIR/frameflow-pg-empty-checksums"
make_postgres_backup_set "$pg_empty"
: >"$pg_empty/SHA256SUMS"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_empty"

pg_duplicate="$TEST_DIR/frameflow-pg-duplicate"
make_postgres_backup_set "$pg_duplicate"
sed -n '1p' "$pg_duplicate/SHA256SUMS" >>"$pg_duplicate/SHA256SUMS"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_duplicate"

pg_unsafe="$TEST_DIR/frameflow-pg-unsafe"
make_postgres_backup_set "$pg_unsafe"
printf '%064d  ../outside\n' 0 >>"$pg_unsafe/SHA256SUMS"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_unsafe"

pg_malformed="$TEST_DIR/frameflow-pg-malformed"
make_postgres_backup_set "$pg_malformed"
printf 'not-a-sha256  manifest.json\n' >"$pg_malformed/SHA256SUMS"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_malformed"

pg_unlisted="$TEST_DIR/frameflow-pg-unlisted"
make_postgres_backup_set "$pg_unlisted"
printf 'unlisted artifact\n' >"$pg_unlisted/extra.bin"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_unlisted"

pg_tampered="$TEST_DIR/frameflow-pg-tampered"
make_postgres_backup_set "$pg_tampered"
printf 'tampered\n' >>"$pg_tampered/dump.custom"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_tampered"

pg_symlink="$TEST_DIR/frameflow-pg-symlink"
make_postgres_backup_set "$pg_symlink"
ln -s dump.custom "$pg_symlink/untrusted-link"
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$pg_symlink"

minio_missing="$TEST_DIR/frameflow-minio-missing-object"
make_minio_backup_set "$minio_missing"
find "$minio_missing/objects" -type f -name second.bin -delete
expect_fail "$BACKUP/verify-backup-set.sh" --backup-dir "$minio_missing"

grep -q 'FRAMEFLOW_BACKUP_LIB_ROOT' "$BACKUP/lib/common.sh"
if grep -Eq 'dirname[[:space:]]+"?\$0|dirname[[:space:]]+\$\{0\}' "$BACKUP/lib/common.sh"; then
  echo "sourced common.sh derives its path from \$0" >&2
  exit 1
fi

echo "F10 backup safety tests: PASS"
