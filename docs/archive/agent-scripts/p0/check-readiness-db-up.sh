#!/bin/sh
set -eu

. "$(dirname -- "$0")/common.sh"

OUTPUT_FILE="${FRAMEFLOW_READINESS_UP_OUTPUT:-$EVIDENCE_DIR/readiness-db-up.txt}"

cleanup() {
    stop_application
}
trap cleanup EXIT HUP INT TERM

ensure_postgres
start_application
probe_until "/readiness" "200" '{"status":"READY"}' 30

{
    printf 'Evidence: EV-FF-P0-001-04\n'
    printf 'Subject commit: %s\n' "$(subject_commit)"
    printf 'Database state: HEALTHY\n'
    printf 'GET /readiness status: %s\n' "$PROBE_CODE"
    printf 'GET /readiness body: %s\n' "$PROBE_BODY"
    printf 'Result: PASS\n'
} > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"
