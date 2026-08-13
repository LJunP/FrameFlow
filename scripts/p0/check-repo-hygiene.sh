#!/bin/sh
set -eu

. "$(dirname -- "$0")/common.sh"

OUTPUT_FILE="${FRAMEFLOW_HYGIENE_OUTPUT:-$EVIDENCE_DIR/repo-hygiene.txt}"
REQUIRE_ZCODE="${FRAMEFLOW_HYGIENE_REQUIRE_ZCODE:-true}"
TRACKED_LIST=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-tracked.XXXXXX")
VIOLATIONS=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-violations.XXXXXX")
ARCHIVE_FILE=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-archive.XXXXXX")
ARCHIVE_LIST=$(mktemp "${TMPDIR:-/tmp}/frameflow-p0-archive-list.XXXXXX")

cleanup() {
    rm -f "$TRACKED_LIST" "$VIOLATIONS" "$ARCHIVE_FILE" "$ARCHIVE_LIST"
}
trap cleanup EXIT HUP INT TERM

cd "$FRAMEFLOW_ROOT"
git ls-files > "$TRACKED_LIST"

while IFS= read -r tracked_path; do
    case "$tracked_path" in
        .zcode/*|.codex/*|.env|.env.*|*.pem|*.key|*.p12|*.pfx|*.jks|*.keystore|target/*|*/target/*|logs/*|*/logs/*|data/*|*/data/*|.idea/*|*/.idea/*|.vscode/*|*/.vscode/*|*.iml|*.log|.DS_Store|*/.DS_Store|__MACOSX/*|*/__MACOSX/*)
            [ "$tracked_path" = ".env.example" ] || printf '%s\n' "$tracked_path" >> "$VIOLATIONS"
            ;;
    esac
done < "$TRACKED_LIST"

if [ -s "$VIOLATIONS" ]; then
    echo "Tracked hygiene violations:" >&2
    cat "$VIOLATIONS" >&2
    exit 1
fi

git check-ignore --no-index -q .zcode/local-plan.md
if [ "$REQUIRE_ZCODE" = "true" ] && [ ! -d .zcode ]; then
    echo "Local .zcode directory must be preserved for the formal P0 evidence run." >&2
    exit 1
fi

if git ls-files --error-unmatch .zcode >/dev/null 2>&1 || [ -n "$(git ls-files '.zcode/**')" ]; then
    echo ".zcode must not be tracked." >&2
    exit 1
fi

if git grep -I -q -E 'BEGIN (RSA |OPENSSH |EC |DSA )?PRIVATE KEY' HEAD -- .; then
    echo "A private-key marker is present in tracked content." >&2
    exit 1
fi

if [ -n "${USER:-}" ] && git grep -I -q "/Users/$USER/" HEAD -- .; then
    echo "A personal absolute path is present in tracked content." >&2
    exit 1
fi

git archive --format=tar HEAD > "$ARCHIVE_FILE"
tar -tf "$ARCHIVE_FILE" > "$ARCHIVE_LIST"
while IFS= read -r archive_path; do
    case "$archive_path" in
        .zcode|.zcode/*|.codex|.codex/*|evidence|evidence/*|docs/08-learning/legacy|docs/08-learning/legacy/*)
            printf '%s\n' "$archive_path" >> "$VIOLATIONS"
            ;;
    esac
done < "$ARCHIVE_LIST"

if [ -s "$VIOLATIONS" ]; then
    echo "Archive hygiene violations:" >&2
    cat "$VIOLATIONS" >&2
    exit 1
fi

status_before=$(git status --porcelain --untracked-files=all)
if [ -n "$status_before" ]; then
    echo "Worktree must be clean before the formal hygiene evidence is written." >&2
    printf '%s\n' "$status_before" >&2
    exit 1
fi

mkdir -p "$(dirname -- "$OUTPUT_FILE")"
{
    printf 'Evidence: EV-FF-P0-001-06\n'
    printf 'Subject commit: %s\n' "$(subject_commit)"
    printf 'Tracked files inspected: %s\n' "$(wc -l < "$TRACKED_LIST" | tr -d ' ')"
    printf 'Archive entries inspected: %s\n' "$(wc -l < "$ARCHIVE_LIST" | tr -d ' ')"
    printf '.zcode local directory required: %s\n' "$REQUIRE_ZCODE"
    printf '.zcode ignored: PASS\n'
    printf '.zcode tracked: NO\n'
    printf '.zcode archived: NO\n'
    printf 'Evidence and legacy material archived: NO\n'
    printf 'Sensitive/generated/platform files tracked: NO\n'
    printf 'Private-key markers in tracked content: NO\n'
    printf 'Personal absolute paths in tracked content: NO\n'
    printf 'Worktree before evidence write: CLEAN\n'
    printf 'Result: PASS\n'
} > "$OUTPUT_FILE"
cat "$OUTPUT_FILE"
