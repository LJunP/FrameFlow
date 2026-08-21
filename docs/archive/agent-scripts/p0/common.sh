#!/bin/sh

# Shared helpers for the P0 acceptance scripts. This file is sourced, not run.

P0_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
FRAMEFLOW_ROOT=$(CDPATH= cd -- "$P0_SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$FRAMEFLOW_ROOT/docker-compose.local.yml"
EVIDENCE_DIR="$FRAMEFLOW_ROOT/evidence/p0"
APP_HOST="127.0.0.1"
APP_PORT="${FRAMEFLOW_EVIDENCE_PORT:-18080}"
APP_PID=""
APP_LOG=""
PROBE_CODE=""
PROBE_BODY=""

mkdir -p "$EVIDENCE_DIR"

compose() {
    docker compose -f "$COMPOSE_FILE" "$@"
}

ensure_postgres() {
    compose up -d --wait postgres >/dev/null
}

find_boot_jar() {
    jar_count=0
    selected_jar=""
    for candidate in "$FRAMEFLOW_ROOT"/frameflow-app/target/frameflow-app-*.jar; do
        [ -f "$candidate" ] || continue
        case "$candidate" in
            *.original) continue ;;
        esac
        jar_count=$((jar_count + 1))
        selected_jar="$candidate"
    done

    if [ "$jar_count" -ne 1 ]; then
        echo "Expected exactly one executable FrameFlow jar, found $jar_count." >&2
        return 1
    fi
    printf '%s\n' "$selected_jar"
}

probe_until() {
    probe_path=$1
    expected_code=$2
    expected_body=$3
    max_attempts=${4:-45}
    probe_file=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-probe.XXXXXX")
    attempt=1

    while [ "$attempt" -le "$max_attempts" ]; do
        PROBE_CODE=$(curl --silent --show-error --connect-timeout 2 --max-time 5 \
            --output "$probe_file" --write-out '%{http_code}' \
            "http://$APP_HOST:$APP_PORT$probe_path" 2>/dev/null || true)
        PROBE_BODY=$(tr -d '\r\n' < "$probe_file")
        if [ "$PROBE_CODE" = "$expected_code" ] && [ "$PROBE_BODY" = "$expected_body" ]; then
            rm -f "$probe_file"
            return 0
        fi
        if [ -n "$APP_PID" ] && ! kill -0 "$APP_PID" 2>/dev/null; then
            rm -f "$probe_file"
            echo "FrameFlow process exited before $probe_path became available." >&2
            return 1
        fi
        attempt=$((attempt + 1))
        sleep 1
    done

    rm -f "$probe_file"
    echo "Timed out waiting for $probe_path (last status=$PROBE_CODE, body=$PROBE_BODY)." >&2
    return 1
}

start_application() {
    boot_jar=$(find_boot_jar)
    APP_LOG=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-app.XXXXXX")
    FRAMEFLOW_SERVER_ADDRESS="$APP_HOST" \
    FRAMEFLOW_SERVER_PORT="$APP_PORT" \
    java -jar "$boot_jar" >"$APP_LOG" 2>&1 &
    APP_PID=$!

    if ! probe_until "/health" "200" '{"status":"UP"}' 60; then
        sanitize_file "$APP_LOG" /dev/stderr
        return 1
    fi
}

stop_application() {
    if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then
        kill "$APP_PID" 2>/dev/null || true
        wait "$APP_PID" 2>/dev/null || true
    fi
    APP_PID=""
    if [ -n "$APP_LOG" ]; then
        rm -f "$APP_LOG"
    fi
    APP_LOG=""
}

subject_commit() {
    git -C "$FRAMEFLOW_ROOT" rev-parse HEAD
}

sanitize_file() {
    source_file=$1
    destination_file=$2
    sed -e "s|$FRAMEFLOW_ROOT|<WORKSPACE>|g" \
        -e "s|${HOME:-/nonexistent}|<USER_HOME>|g" \
        "$source_file" > "$destination_file"
}
