#!/bin/sh
#
# EV-FF-M01H-001-01: run the exact M01-H build command and store a sanitized log.
# This script intentionally does not require a clean worktree.
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
FRAMEFLOW_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)

EVIDENCE_DIR="$FRAMEFLOW_ROOT/evidence/m01h"
RESULT_FILE="$EVIDENCE_DIR/mvn-verify.txt"
RAW_LOG=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01h-build.XXXXXX")
trap 'rm -f "$RAW_LOG"' EXIT INT TERM
BASE_COMMIT=$(git -C "$FRAMEFLOW_ROOT" rev-parse HEAD)
SUBJECT_STATUS=$(git -C "$FRAMEFLOW_ROOT" status --porcelain=v1 --untracked-files=all -- . \
    ':(exclude)evidence/m01h/**' \
    ':(exclude)docs/05-engineering/tasks/M01H/FF-M01H-001.json' \
    ':(exclude)docs/09-delivery/evidence-index.md' \
    ':(exclude)docs/05-engineering/generated/task-capsule-index.md' \
    ':(exclude)docs/05-engineering/task-capsule-catalog.md' \
    ':(exclude)docs/05-engineering/development-plan-p0-m17.md' \
    ':(exclude)docs/00-governance/project-status.md' \
    ':(exclude)docs/00-governance/下次继续FrameFlow开发启动指南.md' \
    ':(exclude)docs/00-governance/正式开发前检查清单.md' \
    ':(exclude)README.md')
if [ -n "$SUBJECT_STATUS" ]; then
    WORKTREE_STATE="DIRTY/UNCOMMITTED"
    FORMAL_SUBJECT_COMMIT="NOT_AVAILABLE"
else
    WORKTREE_STATE="CLEAN"
    FORMAL_SUBJECT_COMMIT="$BASE_COMMIT"
fi
mkdir -p "$EVIDENCE_DIR"

{
    echo "FrameFlow M01-H clean verify evidence"
    echo "base commit:    $BASE_COMMIT"
    echo "worktree:       $WORKTREE_STATE"
    echo "formal subject commit: $FORMAL_SUBJECT_COMMIT"
    echo "generated at:  $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "command:       ./mvnw -B clean verify"
    echo ""
} > "$RESULT_FILE"

(cd "$FRAMEFLOW_ROOT" && ./mvnw -B clean verify) > "$RAW_LOG" 2>&1
BUILD_EXIT=$?

python3 -c '
import re
import sys

text = sys.stdin.read()
workspace = sys.argv[1]
user_home = sys.argv[2]
text = text.replace(workspace, "<WORKSPACE>")
if user_home:
    text = text.replace(user_home, "<USER_HOME>")
text = re.sub(r"/Users/[^/\s]+", "<USER_HOME>", text)
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
text = re.sub(r"[ \t]+$", "", text, flags=re.MULTILINE)
sys.stdout.write(text)
' "$FRAMEFLOW_ROOT" "${HOME:-}" < "$RAW_LOG" >> "$RESULT_FILE"

{
    echo ""
    echo "exit code:     $BUILD_EXIT"
    if [ "$BUILD_EXIT" -eq 0 ]; then
        echo "result:        PASS"
    else
        echo "result:        FAIL"
    fi
} >> "$RESULT_FILE"

rm -f "$RAW_LOG"
trap - EXIT INT TERM
exit "$BUILD_EXIT"
