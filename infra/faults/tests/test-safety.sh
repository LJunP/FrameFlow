#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd -P)"
FAULTS="$ROOT/infra/faults"
TEST_DIR="$(mktemp -d /tmp/frameflow-f10-fault-safety.XXXXXX)"
cleanup() {
  find "$TEST_DIR" -type f -delete
  find "$TEST_DIR" -depth -type d -empty -delete
}
trap cleanup EXIT
compose="$TEST_DIR/docker-compose.yml"
env_file="$TEST_DIR/frameflow.env"
printf 'services:\n  redis:\n    image: redis:7\n    environment:\n      FRAMEFLOW_ENV: local\n' >"$compose"
printf 'FRAMEFLOW_ENV=local\n' >"$env_file"

expect_fail() {
  if "$@" >/dev/null 2>&1; then
    echo "expected failure but command passed: $*" >&2
    exit 1
  fi
}

for scenario in redis-unavailable db-unavailable worker-crash mq-backlog; do
  PATH="/usr/bin:/bin" "$FAULTS/run-drill.sh" --scenario "$scenario" --environment local \
    --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
    --health-url http://127.0.0.1:18080/actuator/health \
    --evidence-dir "$TEST_DIR/frameflow-evidence" >/dev/null
done
[[ ! -e "$TEST_DIR/frameflow-evidence" ]] || { echo "dry-run wrote evidence" >&2; exit 1; }

expect_fail "$FAULTS/run-drill.sh" --scenario redis-unavailable --environment production \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-production \
  --health-url http://127.0.0.1:18080/actuator/health --evidence-dir "$TEST_DIR/frameflow-evidence"
expect_fail "$FAULTS/run-drill.sh" --scenario db-unavailable --environment staging \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-staging \
  --health-url http://127.0.0.1:18080/actuator/health --evidence-dir "$TEST_DIR/frameflow-evidence"
expect_fail "$FAULTS/run-drill.sh" --scenario redis-unavailable --environment local \
  --compose-file "$compose" --env-file "$env_file" --project-name frameflow-local \
  --health-url https://example.com/actuator/health --evidence-dir "$TEST_DIR/frameflow-evidence"

echo "F10 fault harness safety tests: PASS"
