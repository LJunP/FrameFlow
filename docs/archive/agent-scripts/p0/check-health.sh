#!/bin/sh
set -eu

. "$(dirname -- "$0")/common.sh"

OUTPUT_FILE="${FRAMEFLOW_HEALTH_OUTPUT:-$EVIDENCE_DIR/health.txt}"

cleanup() {
    stop_application
}
trap cleanup EXIT HUP INT TERM

ensure_postgres
start_application
probe_until "/health" "200" '{"status":"UP"}' 10

{
    printf 'Evidence: EV-FF-P0-001-02\n'
    printf 'Subject commit: %s\n' "$(subject_commit)"
    printf 'Request: GET /health\n'
    printf 'HTTP status: %s\n' "$PROBE_CODE"
    printf 'Response body: %s\n' "$PROBE_BODY"
    printf 'Result: PASS\n'
} > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"
