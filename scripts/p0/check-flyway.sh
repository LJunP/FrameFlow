#!/bin/sh
set -eu

. "$(dirname -- "$0")/common.sh"

OUTPUT_FILE="${FRAMEFLOW_FLYWAY_OUTPUT:-$EVIDENCE_DIR/flyway-migrate.txt}"
DB_NAME="frameflow_p0_migration_$$"
DB_USER="${FRAMEFLOW_DB_USERNAME:-frameflow}"
DB_PASSWORD="${FRAMEFLOW_DB_PASSWORD:-frameflow_local_only}"
DB_PORT="${FRAMEFLOW_POSTGRES_PORT:-54329}"
FIRST_LOG=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-flyway-first.XXXXXX")
SECOND_LOG=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-flyway-second.XXXXXX")
FIRST_SAFE=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-flyway-first-safe.XXXXXX")
SECOND_SAFE=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-flyway-second-safe.XXXXXX")
DATABASE_CREATED=0

cleanup() {
    exit_code=$?
    trap - EXIT HUP INT TERM
    if [ "$DATABASE_CREATED" -eq 1 ]; then
        compose exec -T postgres psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
            -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
        compose exec -T postgres psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
            -c "DROP DATABASE IF EXISTS \"$DB_NAME\";" >/dev/null 2>&1 || true
    fi
    rm -f "$FIRST_LOG" "$SECOND_LOG" "$FIRST_SAFE" "$SECOND_SAFE"
    exit "$exit_code"
}
trap cleanup EXIT HUP INT TERM

ensure_postgres
boot_jar=$(find_boot_jar)
compose exec -T postgres psql -U "$DB_USER" -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE \"$DB_NAME\";" >/dev/null
DATABASE_CREATED=1

run_migration() {
    log_file=$1
    FRAMEFLOW_DB_URL="jdbc:postgresql://127.0.0.1:$DB_PORT/$DB_NAME" \
    FRAMEFLOW_DB_USERNAME="$DB_USER" \
    FRAMEFLOW_DB_PASSWORD="$DB_PASSWORD" \
    java -jar "$boot_jar" \
        --spring.main.web-application-type=none \
        --frameflow.migration-only=true >"$log_file" 2>&1
}

history_snapshot() {
    compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -At -v ON_ERROR_STOP=1 \
        -c "SELECT version || '|' || script || '|' || checksum || '|' || success FROM flyway_schema_history ORDER BY installed_rank;"
}

run_migration "$FIRST_LOG"
first_history=$(history_snapshot)
first_count=$(compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -At -v ON_ERROR_STOP=1 \
    -c "SELECT count(*) FROM flyway_schema_history WHERE success = true;")
baseline_count=$(compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -At -v ON_ERROR_STOP=1 \
    -c "SELECT count(*) FROM frameflow_schema_baseline WHERE id = 1;")

run_migration "$SECOND_LOG"
second_history=$(history_snapshot)
second_count=$(compose exec -T postgres psql -U "$DB_USER" -d "$DB_NAME" -At -v ON_ERROR_STOP=1 \
    -c "SELECT count(*) FROM flyway_schema_history WHERE success = true;")

[ "$first_count" = "1" ]
[ "$baseline_count" = "1" ]
[ "$second_count" = "$first_count" ]
[ "$second_history" = "$first_history" ]

sanitize_file "$FIRST_LOG" "$FIRST_SAFE"
sanitize_file "$SECOND_LOG" "$SECOND_SAFE"
{
    printf 'Evidence: EV-FF-P0-001-05\n'
    printf 'Subject commit: %s\n' "$(subject_commit)"
    printf 'Isolated database: frameflow_p0_migration_<pid>\n'
    printf 'First migrate successful rows: %s\n' "$first_count"
    printf 'Baseline technical rows: %s\n' "$baseline_count"
    printf 'Second migrate successful rows: %s\n' "$second_count"
    printf 'History snapshot: %s\n' "$first_history"
    printf '\n--- First migration log ---\n'
    cat "$FIRST_SAFE"
    printf '\n--- Second migration log ---\n'
    cat "$SECOND_SAFE"
    printf '\nResult: PASS (second migration was a no-op)\n'
} > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"
