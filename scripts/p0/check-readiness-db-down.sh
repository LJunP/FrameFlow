#!/bin/sh
set -eu

. "$(dirname -- "$0")/common.sh"

OUTPUT_FILE="${FRAMEFLOW_READINESS_DOWN_OUTPUT:-$EVIDENCE_DIR/readiness-db-down.txt}"
DB_STOPPED=0

cleanup() {
    exit_code=$?
    trap - EXIT HUP INT TERM
    if [ "$DB_STOPPED" -eq 1 ]; then
        ensure_postgres >/dev/null 2>&1 || true
    fi
    stop_application
    exit "$exit_code"
}
trap cleanup EXIT HUP INT TERM

ensure_postgres
start_application
probe_until "/readiness" "200" '{"status":"READY"}' 30

compose stop postgres >/dev/null
DB_STOPPED=1
probe_until "/readiness" "503" '{"status":"NOT_READY"}' 30
down_code=$PROBE_CODE
down_body=$PROBE_BODY

probe_until "/health" "200" '{"status":"UP"}' 10
health_code=$PROBE_CODE
health_body=$PROBE_BODY

ensure_postgres
DB_STOPPED=0
probe_until "/readiness" "200" '{"status":"READY"}' 45

{
    printf 'Evidence: EV-FF-P0-001-03\n'
    printf 'Subject commit: %s\n' "$(subject_commit)"
    printf 'Database state during failure probe: STOPPED\n'
    printf 'GET /readiness status while stopped: %s\n' "$down_code"
    printf 'GET /readiness body while stopped: %s\n' "$down_body"
    printf 'GET /health status while stopped: %s\n' "$health_code"
    printf 'GET /health body while stopped: %s\n' "$health_body"
    printf 'Database state at script completion: HEALTHY\n'
    printf 'Result: PASS\n'
} > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"
