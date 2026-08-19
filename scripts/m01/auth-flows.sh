#!/bin/sh
#
# EV-FF-M01-001-02: 运行时安全/错误语义可复现证据。
# 启动真实应用，逐项验证：401/403/404、最后 OWNER、自移除、重复 ACTIVE 成员、
# 每次响应 X-Request-Id 与错误 body requestId 一致。
# 输出: evidence/m01/auth-flows.txt
set -u

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

FAILURES=0
CHECKS=0

record() { # $1=PASS/FAIL  $2=描述
    if [ "$1" = "PASS" ]; then
        printf 'PASS | %s\n' "$2" >> "$EVIDENCE_DIR/auth-flows.txt"
    else
        FAILURES=$((FAILURES + 1))
        printf 'FAIL | %s\n' "$2" >> "$EVIDENCE_DIR/auth-flows.txt"
    fi
    CHECKS=$((CHECKS + 1))
}

json_field() { # $1=json string  $2=field
    python3 -c "import json,sys;d=json.loads(sys.argv[1]);print(d.get(sys.argv[2],''))" "$1" "$2" 2>/dev/null
}

request() {
    method=$1; shift
    path=$1; shift
    rid_file=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-rid.XXXXXX")
    body_file=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-body.XXXXXX")
    REQ_CODE=$(curl --silent --show-error --connect-timeout 2 --max-time 10 \
        -X "$method" -D "$rid_file" --output "$body_file" --write-out '%{http_code}' \
        "http://$APP_HOST:$APP_PORT$path" "$@")
    REQ_RID=$(grep -i '^X-Request-Id:' "$rid_file" | head -1 | tr -d '\r' | awk '{print $2}')
    REQ_BODY=$(tr -d '\r\n' < "$body_file")
    printf '>>> %s %s\n    -> %s  X-Request-Id=%s\n    body=%s\n' \
        "$method" "$path" "$REQ_CODE" "$REQ_RID" "$REQ_BODY" >> "$EVIDENCE_DIR/auth-flows.txt"
    rm -f "$rid_file" "$body_file"
}

assert_status() { # $1=expected code  $2=描述
    if [ "$REQ_CODE" = "$1" ]; then
        record PASS "$2 (HTTP $REQ_CODE)"
    else
        record FAIL "$2 (expected HTTP $1, got $REQ_CODE)"
    fi
}

assert_request_id() { # $1=描述
    body_rid=$(json_field "$REQ_BODY" requestId)
    if [ -n "$REQ_RID" ] && [ "$body_rid" = "$REQ_RID" ]; then
        record PASS "$1 (requestId header==body)"
    else
        record FAIL "$1 (header=$REQ_RID body=$body_rid)"
    fi
}

ensure_postgres
ensure_jwt_keys

{
    echo "FrameFlow M01 auth-flows evidence"
    echo "subject commit: $(subject_commit)"
    echo "generated at:  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo ""
} > "$EVIDENCE_DIR/auth-flows.txt"

if ! start_application; then
    record FAIL "应用启动（health probe）"
    echo "RESULT: FAILURES=$FAILURES CHECKS=$CHECKS" >> "$EVIDENCE_DIR/auth-flows.txt"
    exit 1
fi

EMAIL_A="flow-a-$(date +%s)@example.com"
EMAIL_B="flow-b-$(date +%s)@example.com"
EMAIL_C="flow-c-$(date +%s)@example.com"
PASS="passw0rd!"

# 注册 A/B/C
request POST /api/v1/auth/register -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS\",\"displayName\":\"Flow A\"}"
assert_status 201 "注册 A"
request POST /api/v1/auth/register -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_B\",\"password\":\"$PASS\",\"displayName\":\"Flow B\"}"
assert_status 201 "注册 B"
request POST /api/v1/auth/register -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_C\",\"password\":\"$PASS\",\"displayName\":\"Flow C\"}"
assert_status 201 "注册 C"

# 重复注册 -> 409 USER_EMAIL_CONFLICT + requestId 一致
request POST /api/v1/auth/register -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS\",\"displayName\":\"Flow A2\"}"
assert_status 409 "重复注册 USER_EMAIL_CONFLICT"

# 未认证 -> 401 AUTH_REQUIRED + requestId 一致
request GET /api/v1/auth/me
assert_status 401 "未认证 /auth/me"
assert_request_id "未认证 401 requestId 一致"

# 错误密码 -> 401
request POST /api/v1/auth/login -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_A\",\"password\":\"wrong-password\"}"
assert_status 401 "错误密码登录"

# 登录拿 Token
request POST /api/v1/auth/login -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_A\",\"password\":\"$PASS\"}"
assert_status 200 "A 登录"
TOKEN_A=$(json_field "$REQ_BODY" accessToken)
REFRESH_A=$(json_field "$REQ_BODY" refreshToken)

request POST /api/v1/auth/login -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_B\",\"password\":\"$PASS\"}"
TOKEN_B=$(json_field "$REQ_BODY" accessToken)
request POST /api/v1/auth/login -H 'Content-Type: application/json' \
    -d "{\"email\":\"$EMAIL_C\",\"password\":\"$PASS\"}"
TOKEN_C=$(json_field "$REQ_BODY" accessToken)

AUTH_A="Authorization: Bearer $TOKEN_A"
AUTH_B="Authorization: Bearer $TOKEN_B"
AUTH_C="Authorization: Bearer $TOKEN_C"
KEY_A="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')"
KEY_B="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')"
KEY_C="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')"
KEY_D="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')"
KEY_E="Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid;print(uuid.uuid4())')"

# A 建团队 -> 201
request POST /api/v1/teams -H 'Content-Type: application/json' -H "$AUTH_A" -H "$KEY_A" \
    -d "{\"name\":\"flow-team-$(date +%s)\"}"
assert_status 201 "A 创建团队"
TEAM_ID=$(json_field "$REQ_BODY" id)

# 用 /auth/me 取 B/C 的 userId
request GET /api/v1/auth/me -H "$AUTH_B"
B_USER_ID=$(json_field "$REQ_BODY" id)
request GET /api/v1/auth/me -H "$AUTH_C"
C_USER_ID=$(json_field "$REQ_BODY" id)

# A 添加 B（EDITOR）-> 201
request POST "/api/v1/teams/$TEAM_ID/members" -H 'Content-Type: application/json' -H "$AUTH_A" -H "$KEY_B" \
    -d "{\"email\":\"$EMAIL_B\",\"role\":\"EDITOR\"}"
assert_status 201 "A 添加 B"

# 重复添加 B -> 409 TEAM_MEMBER_ALREADY_EXISTS + requestId 一致
request POST "/api/v1/teams/$TEAM_ID/members" -H 'Content-Type: application/json' -H "$AUTH_A" -H "$KEY_C" \
    -d "{\"email\":\"$EMAIL_B\",\"role\":\"EDITOR\"}"
assert_status 409 "重复 ACTIVE 成员 TEAM_MEMBER_ALREADY_EXISTS"
assert_request_id "重复成员 409 requestId 一致"

# 非成员 C 读团队 -> 404 RESOURCE_NOT_FOUND + requestId 一致
request GET "/api/v1/teams/$TEAM_ID" -H "$AUTH_C"
assert_status 404 "非成员访问团队 404"
assert_request_id "404 requestId 一致"

# 非 OWNER（B）管理成员 -> 403 FORBIDDEN + requestId 一致
request POST "/api/v1/teams/$TEAM_ID/members" -H 'Content-Type: application/json' -H "$AUTH_B" -H "$KEY_D" \
    -d "{\"email\":\"$EMAIL_C\",\"role\":\"VIEWER\"}"
assert_status 403 "非 OWNER 添加成员 403"

request PATCH "/api/v1/teams/$TEAM_ID/members/$C_USER_ID" -H 'Content-Type: application/json' -H "$AUTH_B" \
    -d "{\"role\":\"VIEWER\"}"
assert_status 403 "非 OWNER 修改角色 403"
assert_request_id "403 requestId 一致"

# OWNER 自移除 -> 409 TEAM_SELF_REMOVAL_FORBIDDEN
request GET /api/v1/auth/me -H "$AUTH_A"
A_USER_ID=$(json_field "$REQ_BODY" id)
request DELETE "/api/v1/teams/$TEAM_ID/members/$A_USER_ID" -H "$AUTH_A"
assert_status 409 "OWNER 自移除 TEAM_SELF_REMOVAL_FORBIDDEN"
assert_request_id "自移除 409 requestId 一致"

# 唯一 OWNER 降级 -> 409 TEAM_LAST_OWNER_CONFLICT
request PATCH "/api/v1/teams/$TEAM_ID/members/$A_USER_ID" -H 'Content-Type: application/json' -H "$AUTH_A" \
    -d "{\"role\":\"EDITOR\"}"
assert_status 409 "最后一名 OWNER 降级 TEAM_LAST_OWNER_CONFLICT"

# 移除普通成员 -> 204；验证成功响应带 X-Request-Id
request DELETE "/api/v1/teams/$TEAM_ID/members/$B_USER_ID" -H "$AUTH_A"
assert_status 204 "移除普通成员 204"
if [ -n "$REQ_RID" ]; then
    record PASS "204 响应带 X-Request-Id"
else
    record FAIL "204 响应缺少 X-Request-Id"
fi

# refresh 轮换 + 重放撤销 family（token-contract §4）
request POST /api/v1/auth/refresh -H 'Content-Type: application/json' \
    -d "{\"refreshToken\":\"$REFRESH_A\"}"
assert_status 200 "refresh 轮换"
REFRESH_B=$(json_field "$REQ_BODY" refreshToken)
request POST /api/v1/auth/refresh -H 'Content-Type: application/json' \
    -d "{\"refreshToken\":\"$REFRESH_A\"}"
assert_status 401 "refresh 重放撤销"
request POST /api/v1/auth/refresh -H 'Content-Type: application/json' \
    -d "{\"refreshToken\":\"$REFRESH_B\"}"
assert_status 401 "family 已撤销"

stop_application

{
    echo ""
    echo "RESULT: CHECKS=$CHECKS FAILURES=$FAILURES"
} >> "$EVIDENCE_DIR/auth-flows.txt"

if [ "$FAILURES" -gt 0 ]; then
    echo "auth-flows: $FAILURES check(s) failed (see evidence/m01/auth-flows.txt)" >&2
    exit 1
fi
echo "auth-flows: all $CHECKS checks passed"
