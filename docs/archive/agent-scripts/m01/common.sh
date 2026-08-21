#!/bin/sh

# Shared helpers for the M01 acceptance scripts. This file is sourced, not run.
# The caller must pass its resolved script directory explicitly. Deriving it from
# $0 inside a sourced file can point two levels above the repository and create
# data/evidence directories outside FrameFlow.

if [ -z "${FRAMEFLOW_M01_CALLER_DIR:-}" ]; then
    echo "common.sh must be sourced by an M01 entry script with FRAMEFLOW_M01_CALLER_DIR set" >&2
    return 2 2>/dev/null || exit 2
fi

M01_SCRIPT_DIR=$(CDPATH= cd -- "$FRAMEFLOW_M01_CALLER_DIR" && pwd)
FRAMEFLOW_ROOT=$(CDPATH= cd -- "$M01_SCRIPT_DIR/../.." && pwd)
if [ ! -f "$FRAMEFLOW_ROOT/pom.xml" ] || [ ! -f "$FRAMEFLOW_ROOT/docker-compose.local.yml" ]; then
    echo "Refusing to create M01 runtime directories: resolved FrameFlow root is invalid" >&2
    return 2 2>/dev/null || exit 2
fi
COMPOSE_FILE="$FRAMEFLOW_ROOT/docker-compose.local.yml"
case "${FRAMEFLOW_M01_EVIDENCE_DIR:-}" in
    "") EVIDENCE_DIR="$FRAMEFLOW_ROOT/evidence/m01" ;;
    "evidence/m01"|"evidence/m01h") EVIDENCE_DIR="$FRAMEFLOW_ROOT/$FRAMEFLOW_M01_EVIDENCE_DIR" ;;
    "$FRAMEFLOW_ROOT/evidence/m01"|"$FRAMEFLOW_ROOT/evidence/m01h") EVIDENCE_DIR="$FRAMEFLOW_M01_EVIDENCE_DIR" ;;
    *)
        echo "FRAMEFLOW_M01_EVIDENCE_DIR must be evidence/m01 or evidence/m01h" >&2
        exit 2
        ;;
esac
KEY_DIR="$FRAMEFLOW_ROOT/data/jwt"
APP_HOST="127.0.0.1"
APP_PORT="${FRAMEFLOW_EVIDENCE_PORT:-18081}"
APP_PID=""
APP_LOG=""
PROBE_CODE=""
PROBE_BODY=""
JWT_KID="${FRAMEFLOW_JWT_KID:-local-m01-kid}"

evidence_subject_status() {
    git -C "$FRAMEFLOW_ROOT" status --porcelain=v1 --untracked-files=all -- . \
        ':(exclude)evidence/m01h/**' \
        ':(exclude)docs/05-engineering/tasks/M01H/FF-M01H-001.json' \
        ':(exclude)docs/09-delivery/evidence-index.md' \
        ':(exclude)docs/05-engineering/generated/task-capsule-index.md' \
        ':(exclude)docs/05-engineering/task-capsule-catalog.md' \
        ':(exclude)docs/05-engineering/development-plan-p0-m17.md' \
        ':(exclude)docs/00-governance/project-status.md' \
        ':(exclude)docs/00-governance/下次继续FrameFlow开发启动指南.md' \
        ':(exclude)docs/00-governance/正式开发前检查清单.md' \
        ':(exclude)README.md'
}

EVIDENCE_BASE_COMMIT=$(git -C "$FRAMEFLOW_ROOT" rev-parse HEAD)
if [ -n "$(evidence_subject_status)" ]; then
    EVIDENCE_WORKTREE_STATE="DIRTY/UNCOMMITTED"
    EVIDENCE_FORMAL_SUBJECT_COMMIT="NOT_AVAILABLE"
else
    EVIDENCE_WORKTREE_STATE="CLEAN"
    EVIDENCE_FORMAL_SUBJECT_COMMIT="$EVIDENCE_BASE_COMMIT"
fi

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
    sanitize_stream < "$source_file" > "$destination_file"
}

sanitize_stream() {
    python3 -c '
import re
import sys

text = sys.stdin.read()
text = re.sub(
    r"-----BEGIN (?:RSA )?PRIVATE KEY-----.*?-----END (?:RSA )?PRIVATE KEY-----",
    "<REDACTED PRIVATE KEY>",
    text,
    flags=re.DOTALL,
)
text = re.sub(
    r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}",
    "<REDACTED>",
    text,
)
text = re.sub(
    r"(\"(?i:(?:access|refresh)[_-]?token)\"\s*:\s*\")[^\"]*(\")",
    r"\1<REDACTED>\2",
    text,
)
text = re.sub(r"(?i)\bBearer\s+\S+", "Bearer <REDACTED>", text)
sys.stdout.write(text)
' | sed -e "s|$FRAMEFLOW_ROOT|<WORKSPACE>|g" \
        -e "s|${HOME:-/nonexistent}|<USER_HOME>|g" \
        -e 's|/Users/[^/[:space:]]*|<USER_HOME>|g'
}
