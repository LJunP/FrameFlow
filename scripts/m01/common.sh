#!/bin/sh

# Shared helpers for the M01 acceptance scripts. This file is sourced, not run.

M01_SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
FRAMEFLOW_ROOT=$(CDPATH= cd -- "$M01_SCRIPT_DIR/../.." && pwd)
COMPOSE_FILE="$FRAMEFLOW_ROOT/docker-compose.local.yml"
EVIDENCE_DIR="$FRAMEFLOW_ROOT/evidence/m01"
KEY_DIR="$FRAMEFLOW_ROOT/data/jwt"
APP_HOST="127.0.0.1"
APP_PORT="${FRAMEFLOW_EVIDENCE_PORT:-18081}"
APP_PID=""
APP_LOG=""
PROBE_CODE=""
PROBE_BODY=""
JWT_KID="${FRAMEFLOW_JWT_KID:-local-m01-kid}"

mkdir -p "$EVIDENCE_DIR" "$KEY_DIR"

compose() {
    docker compose -f "$COMPOSE_FILE" "$@"
}

ensure_postgres() {
    compose up -d --wait postgres >/dev/null
}

ensure_jwt_keys() {
    if [ ! -f "$KEY_DIR/private.pem" ] || [ ! -f "$KEY_DIR/public.pem" ]; then
        openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
            -out "$KEY_DIR/private.pem" >/dev/null 2>&1
        openssl pkey -in "$KEY_DIR/private.pem" -pubout -out "$KEY_DIR/public.pem" >/dev/null 2>&1
    fi
    export FRAMEFLOW_JWT_KID="$JWT_KID"
    export FRAMEFLOW_JWT_PRIVATE_KEY_PEM="$(cat "$KEY_DIR/private.pem")"
    export FRAMEFLOW_JWT_PUBLIC_KEY_PEM="$(cat "$KEY_DIR/public.pem")"
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
    printf "%s\n" "$selected_jar"
}

probe_until() {
    probe_path=$1
    expected_code=$2
    expected_body=$3
    max_attempts=${4:-90}
    probe_file=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-probe.XXXXXX")
    attempt=1

    while [ "$attempt" -le "$max_attempts" ]; do
        PROBE_CODE=$(curl --silent --show-error --connect-timeout 2 --max-time 5 \
            --output "$probe_file" --write-out "%{http_code}" \
            "http://$APP_HOST:$APP_PORT$probe_path" 2>/dev/null || true)
        PROBE_BODY=$(tr -d "\r\n" < "$probe_file")
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
    ensure_jwt_keys
    APP_LOG=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-app.XXXXXX")
    FRAMEFLOW_SERVER_ADDRESS="$APP_HOST" \
    FRAMEFLOW_SERVER_PORT="$APP_PORT" \
    java -jar "$boot_jar" >"$APP_LOG" 2>&1 &
    APP_PID=$!

    if ! probe_until "/health" "200" "{\"status\":\"UP\"}" 120; then
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
