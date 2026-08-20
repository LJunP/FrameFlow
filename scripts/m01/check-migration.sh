#!/bin/sh
#
# EV-FF-M01-001-03 / EV-FF-M01H-001-04: 隔离空库 forward-only 迁移证据。
# 在 compose postgres 中创建全新隔离数据库：第一次启动应用执行当前全部 V* 迁移，
# 第二次启动验证无新迁移发生（repeat -> no change）；并断言表集合包含
# refresh_token_sessions/idempotency_records 且不包含 project_members。
# 输出: ${FRAMEFLOW_M01_EVIDENCE_DIR:-evidence/m01}/migration.txt
set -u

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

FAILURES=0
CHECKS=0

record() { # $1=PASS/FAIL  $2=描述
    if [ "$1" = "PASS" ]; then
        printf 'PASS | %s\n' "$2" >> "$EVIDENCE_DIR/migration.txt"
    else
        FAILURES=$((FAILURES + 1))
        printf 'FAIL | %s\n' "$2" >> "$EVIDENCE_DIR/migration.txt"
    fi
    CHECKS=$((CHECKS + 1))
}

ensure_postgres
ensure_jwt_keys

BOOT_JAR=$(find_boot_jar) || exit 1
BOOT_JAR_REF=${BOOT_JAR#"$FRAMEFLOW_ROOT"/}
MIGRATION_DIR="$FRAMEFLOW_ROOT/frameflow-app/src/main/resources/db/migration"
AVAILABLE_VERSIONS=$(find "$MIGRATION_DIR" -type f -name 'V[0-9]*__*.sql' -exec basename {} \; \
    | sed -n 's/^V\([0-9][0-9]*\)__.*$/\1/p' | sort -n)
EXPECTED_HISTORY=$(printf '%s\n' "$AVAILABLE_VERSIONS" | sed '/^$/d' | paste -sd, -)
EXPECTED_MIGRATION_COUNT=$(printf '%s\n' "$AVAILABLE_VERSIONS" | awk 'NF { count++ } END { print count + 0 }')
MIGRATION_LABELS=$(printf '%s\n' "$AVAILABLE_VERSIONS" | awk 'NF { print "V" $0 }' | paste -sd+ -)

if [ "$EXPECTED_MIGRATION_COUNT" -eq 0 ]; then
    echo "no versioned Flyway migration found in frameflow-app/src/main/resources/db/migration" >&2
    exit 1
fi

CID=$(compose ps -q postgres | head -1)
if [ -z "$CID" ]; then
    echo "postgres container not running" >&2
    exit 1
fi

PG_HOST="127.0.0.1"
PG_PORT="${FRAMEFLOW_POSTGRES_PORT:-54329}"
PG_USER="${FRAMEFLOW_DB_USERNAME:-frameflow}"
PG_PASS="${FRAMEFLOW_DB_PASSWORD:-frameflow_local_only}"
DB_NAME="m01_mig_$(date +%s)"
APP_PORT="19081"

psql_exec() { # $1=db  $2=sql
    docker exec "$CID" psql -U "$PG_USER" -d "$1" -tAc "$2" 2>/dev/null
}

{
    echo "FrameFlow M01 migration evidence (isolated empty database)"
    echo "base commit:    $EVIDENCE_BASE_COMMIT"
    echo "worktree:       $EVIDENCE_WORKTREE_STATE"
    echo "formal subject commit: $EVIDENCE_FORMAL_SUBJECT_COMMIT"
    echo "generated at:  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "isolated db:   $DB_NAME (created and dropped by this script)"
    echo "boot jar:      $BOOT_JAR_REF"
    echo ""
} > "$EVIDENCE_DIR/migration.txt"

cleanup() {
    docker exec "$CID" dropdb -U "$PG_USER" --if-exists "$DB_NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

if ! docker exec "$CID" createdb -U "$PG_USER" "$DB_NAME" >/dev/null 2>&1; then
    echo "failed to create isolated database $DB_NAME" >&2
    record FAIL "创建隔离数据库"
    exit 1
fi
record PASS "创建隔离空库 $DB_NAME"

DB_URL="jdbc:postgresql://$PG_HOST:$PG_PORT/$DB_NAME"

# ---------- 第一次启动：应应用当前全部版本化迁移 ----------
LOG1=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-mig1.XXXXXX")
FRAMEFLOW_DB_URL="$DB_URL" FRAMEFLOW_SERVER_PORT="$APP_PORT" \
    java -jar "$BOOT_JAR" > "$LOG1" 2>&1 &
PID1=$!
OK1=0
for i in $(seq 1 120); do
    code=$(curl --silent --connect-timeout 2 --max-time 3 -o /dev/null -w '%{http_code}' "http://$APP_HOST:$APP_PORT/health" 2>/dev/null || true)
    if [ "$code" = "200" ]; then OK1=1; break; fi
    if ! kill -0 "$PID1" 2>/dev/null; then break; fi
    sleep 1
done
if [ "$OK1" = "1" ]; then
    record PASS "第一次启动成功（隔离库迁移 ${MIGRATION_LABELS} 后应用就绪）"
else
    record FAIL "第一次启动未就绪"
    sanitize_file "$LOG1" /dev/stderr
    kill "$PID1" 2>/dev/null || true
    wait "$PID1" 2>/dev/null || true
    echo "RESULT: CHECKS=$CHECKS FAILURES=$FAILURES" >> "$EVIDENCE_DIR/migration.txt"
    exit 1
fi
kill "$PID1" 2>/dev/null || true
wait "$PID1" 2>/dev/null || true
sleep 2

HISTORY1=$(psql_exec "$DB_NAME" "SELECT version FROM flyway_schema_history WHERE success IS TRUE ORDER BY installed_rank" \
    | sed '/^$/d; s/[[:space:]]//g' | paste -sd, -)
echo "run1 flyway history: [$HISTORY1]" >> "$EVIDENCE_DIR/migration.txt"
if [ "$HISTORY1" = "$EXPECTED_HISTORY" ]; then
    record PASS "${MIGRATION_LABELS} 迁移成功（schema_history: ${HISTORY1}）"
else
    record FAIL "schema_history 应为 ${EXPECTED_HISTORY}，实际: $HISTORY1"
fi

# ---------- 第二次启动：应无变更（no migration necessary） ----------
LOG2=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-mig2.XXXXXX")
ROWS_BEFORE=$(psql_exec "$DB_NAME" "SELECT count(*) FROM flyway_schema_history")
FRAMEFLOW_DB_URL="$DB_URL" FRAMEFLOW_SERVER_PORT="$APP_PORT" \
    java -jar "$BOOT_JAR" > "$LOG2" 2>&1 &
PID2=$!
OK2=0
for i in $(seq 1 120); do
    code=$(curl --silent --connect-timeout 2 --max-time 3 -o /dev/null -w '%{http_code}' "http://$APP_HOST:$APP_PORT/health" 2>/dev/null || true)
    if [ "$code" = "200" ]; then OK2=1; break; fi
    if ! kill -0 "$PID2" 2>/dev/null; then break; fi
    sleep 1
done
if [ "$OK2" = "1" ]; then
    record PASS "第二次启动成功（重复迁移无变更）"
else
    record FAIL "第二次启动未就绪"
    sanitize_file "$LOG2" /dev/stderr
    kill "$PID2" 2>/dev/null || true
    wait "$PID2" 2>/dev/null || true
    echo "RESULT: CHECKS=$CHECKS FAILURES=$FAILURES" >> "$EVIDENCE_DIR/migration.txt"
    exit 1
fi
kill "$PID2" 2>/dev/null || true
wait "$PID2" 2>/dev/null || true
ROWS_AFTER=$(psql_exec "$DB_NAME" "SELECT count(*) FROM flyway_schema_history")
echo "run2 flyway history rows: before=$ROWS_BEFORE after=$ROWS_AFTER" >> "$EVIDENCE_DIR/migration.txt"
if [ "$ROWS_BEFORE" = "$ROWS_AFTER" ] && [ "$ROWS_AFTER" = "$EXPECTED_MIGRATION_COUNT" ]; then
    record PASS "第二次执行无新迁移（rows=${ROWS_AFTER}）"
else
    record FAIL "第二次执行迁移行数异常（expected=$EXPECTED_MIGRATION_COUNT before=$ROWS_BEFORE after=${ROWS_AFTER}）"
fi

# ---------- 表集合边界 ----------
TABLE_CNT=$(psql_exec "$DB_NAME" "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('users','teams','team_members','refresh_token_sessions','idempotency_records')")
if [ "$TABLE_CNT" = "5" ]; then
    record PASS "包含 users/teams/team_members/refresh_token_sessions/idempotency_records"
else
    record FAIL "身份表集合缺失（count=${TABLE_CNT}）"
fi
PROJECT_ROWS=$(psql_exec "$DB_NAME" "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='project_members'")
if [ "$PROJECT_ROWS" = "0" ]; then
    record PASS "不包含 project_members（边界正确）"
else
    record FAIL "意外包含 project_members（count=${PROJECT_ROWS}）"
fi

# ---------- 迁移日志摘录（保留证据原文） ----------
echo "" >> "$EVIDENCE_DIR/migration.txt"
echo "--- run1 flyway log lines ---" >> "$EVIDENCE_DIR/migration.txt"
grep -E 'Flyway|flyway|migrat|Migrat' "$LOG1" | sanitize_stream | sed -n 1,20p >> "$EVIDENCE_DIR/migration.txt" 2>/dev/null || true
echo "--- run2 flyway log lines ---" >> "$EVIDENCE_DIR/migration.txt"
grep -E 'Flyway|flyway|migrat|Migrat' "$LOG2" | sanitize_stream | sed -n 1,20p >> "$EVIDENCE_DIR/migration.txt" 2>/dev/null || true

rm -f "$LOG1" "$LOG2"
docker exec "$CID" dropdb -U "$PG_USER" "$DB_NAME" >/dev/null 2>&1 || true
trap - EXIT INT TERM

{
    echo ""
    echo "RESULT: CHECKS=$CHECKS FAILURES=$FAILURES"
} >> "$EVIDENCE_DIR/migration.txt"

if [ "$FAILURES" -gt 0 ]; then
    echo "check-migration: $FAILURES check(s) failed (see $EVIDENCE_DIR/migration.txt)" >&2
    exit 1
fi
echo "check-migration: all $CHECKS checks passed"
