#!/bin/sh
#
# EV-FF-M01-001-04: 运行时 OpenAPI 与手写权威契约差异校验证据。
# 启动真实应用，抓取 springdoc /v3/api-docs，用与 OpenApiDiffTest 完全相同的
# OpenApiNormalizer（Java 类，测试作用域）规范化后与 docs/04-api/openapi/frameflow-v1.yaml
# 比对，无未批准差异即 PASS。
# 输出: evidence/m01/openapi-diff.txt
set -u

. "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)/common.sh"

RUNTIME_JSON=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-openapi-runtime.XXXXXX")
CP_FILE=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01-cp.XXXXXX")
RESULT_FILE="$EVIDENCE_DIR/openapi-diff.txt"

ensure_postgres
ensure_jwt_keys

{
    echo "FrameFlow M01 runtime OpenAPI <-> authoritative contract diff evidence"
    echo "subject commit: $(subject_commit)"
    echo "generated at:  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "contract:      docs/04-api/openapi/frameflow-v1.yaml (docs/04-api 权威契约，未修改)"
    echo ""
} > "$RESULT_FILE"

if ! start_application; then
    echo "FAIL | 应用启动失败（无法取得运行时 OpenAPI）" >> "$RESULT_FILE"
    exit 1
fi

CURL_CODE=$(curl --silent --show-error --connect-timeout 2 --max-time 15 \
    --output "$RUNTIME_JSON" --write-out '%{http_code}' \
    "http://$APP_HOST:$APP_PORT/v3/api-docs")
if [ "$CURL_CODE" != "200" ]; then
    echo "FAIL | /v3/api-docs 返回 $CURL_CODE" >> "$RESULT_FILE"
    stop_application
    rm -f "$RUNTIME_JSON" "$CP_FILE"
    exit 1
fi

# 组合类路径：先安装项目模块到本地仓库，确保 dependency:build-classpath 能解析 identity 构件；
# 再组合测试类（含 OpenApiNormalizer/OpenApiDiffRunner）+ 应用类 + 依赖
if ! (cd "$FRAMEFLOW_ROOT" && ./mvnw -q -pl frameflow-app -am -DskipTests install >/dev/null 2>&1); then
    echo "FAIL | maven install（准备 OpenAPI 比对 classpath）失败" >> "$RESULT_FILE"
    stop_application
    rm -f "$RUNTIME_JSON" "$CP_FILE"
    exit 1
fi
if ! (cd "$FRAMEFLOW_ROOT" && ./mvnw -q -pl frameflow-app dependency:build-classpath \
        -Dmdep.outputFile="$CP_FILE" >/dev/null 2>&1); then
    echo "FAIL | maven dependency:build-classpath 失败" >> "$RESULT_FILE"
    stop_application
    rm -f "$RUNTIME_JSON" "$CP_FILE"
    exit 1
fi

TEST_CLASSES="$FRAMEFLOW_ROOT/frameflow-app/target/test-classes"
APP_CLASSES="$FRAMEFLOW_ROOT/frameflow-app/target/classes"
CP="$TEST_CLASSES:$APP_CLASSES:$(cat "$CP_FILE")"
CONTRACT="$FRAMEFLOW_ROOT/docs/04-api/openapi/frameflow-v1.yaml"

java -cp "$CP" com.frameflow.identity.support.OpenApiDiffRunner \
    "$RUNTIME_JSON" "$CONTRACT" >> "$RESULT_FILE" 2>&1
JAVA_EXIT=$?

{
    echo ""
    echo "runtime doc snapshot: $RUNTIME_JSON"
    echo "comparator:          OpenApiDiffRunner + OpenApiNormalizer (test-scoped, same as TEST-FF-M01-001-08)"
} >> "$RESULT_FILE"

stop_application
rm -f "$RUNTIME_JSON" "$CP_FILE"

if [ "$JAVA_EXIT" -eq 0 ]; then
    echo "check-openapi-diff: PASS (runtime OpenAPI 与手写权威契约无未批准差异)"
    exit 0
fi
echo "check-openapi-diff: FAIL (见 evidence/m01/openapi-diff.txt)" >&2
exit 1
