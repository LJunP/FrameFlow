#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
[[ -f "$SCRIPT_DIR/.f10-fault-root" ]] || { echo "ERROR: fault root sentinel missing" >&2; exit 2; }
bash -n "$SCRIPT_DIR/run-drill.sh" "$SCRIPT_DIR/tests/test-safety.sh"
bash "$SCRIPT_DIR/tests/test-safety.sh"
grep -q 'environment.*local|dev|staging' "$SCRIPT_DIR/run-drill.sh"
grep -q 'frameflow.drill.' "$SCRIPT_DIR/run-drill.sh"
grep -q 'verify_recovery' "$SCRIPT_DIR/run-drill.sh"
grep -q 'delete_drill_queue_strict' "$SCRIPT_DIR/run-drill.sh"
grep -q '看似正式但未签发 HARNESS_PASS 的碎片' "$SCRIPT_DIR/run-drill.sh"
grep -q 'publish message --exchange amq.default --routing-key' "$SCRIPT_DIR/run-drill.sh"
grep -q 'declare queue --name.*--durable true --auto-delete false.*x-expires' "$SCRIPT_DIR/run-drill.sh"
grep -q 'does not trigger the business queue backlog alert' "$SCRIPT_DIR/run-drill.sh"
echo "F10 fault harness static validation: PASS"
