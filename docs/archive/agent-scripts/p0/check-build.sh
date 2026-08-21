#!/bin/sh
set -eu

. "$(dirname -- "$0")/common.sh"

OUTPUT_FILE="${FRAMEFLOW_BUILD_OUTPUT:-$EVIDENCE_DIR/mvn-verify.txt}"
RAW_OUTPUT=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-build.XXXXXX")
SANITIZED_OUTPUT=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-build-sanitized.XXXXXX")

cleanup() {
    rm -f "$RAW_OUTPUT" "$SANITIZED_OUTPUT"
}
trap cleanup EXIT HUP INT TERM

mkdir -p "$(dirname -- "$OUTPUT_FILE")"
cd "$FRAMEFLOW_ROOT"

build_code=0
DOCKER_API_VERSION="${DOCKER_API_VERSION:-1.44}" mvn -B clean verify >"$RAW_OUTPUT" 2>&1 || build_code=$?
sanitize_file "$RAW_OUTPUT" "$SANITIZED_OUTPUT"

{
    cat "$SANITIZED_OUTPUT"
    printf '\nP0 evidence subject commit: %s\n' "$(subject_commit)"
    printf 'Command: mvn -B clean verify\n'
    printf 'Exit code: %s\n' "$build_code"
} > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"

if [ "$build_code" -ne 0 ]; then
    exit "$build_code"
fi
grep -q 'BUILD SUCCESS' "$OUTPUT_FILE"
grep -Eq 'Tests run: [1-9][0-9]*' "$OUTPUT_FILE"
if grep -q 'Tests are skipped' "$OUTPUT_FILE"; then
    echo "Build reported skipped tests." >&2
    exit 1
fi

printf 'Result: PASS\n' | tee -a "$OUTPUT_FILE"
