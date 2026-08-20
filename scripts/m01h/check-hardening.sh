#!/bin/sh
#
# EV-FF-M01H-001-02: governance and evidence hardening checks.
# Read-only with respect to the repository except for its own sanitized evidence file.
# A dirty worktree is expected and is audited; cleanliness is not an acceptance condition.
set -u

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
FRAMEFLOW_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/../.." && pwd)
EVIDENCE_DIR="$FRAMEFLOW_ROOT/evidence/m01h"
RESULT_FILE="$EVIDENCE_DIR/hardening-checks.txt"
TMP_RESULT=$(mktemp "${TMPDIR:-/tmp}/frameflow-m01h-hardening.XXXXXX")
trap 'rm -f "$TMP_RESULT"' EXIT INT TERM

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

python3 - "$FRAMEFLOW_ROOT" "$BASE_COMMIT" "$WORKTREE_STATE" "$FORMAL_SUBJECT_COMMIT" > "$TMP_RESULT" <<'PY'
import fnmatch
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath

root = Path(sys.argv[1]).resolve()
base_commit = sys.argv[2]
worktree_state = sys.argv[3]
formal_subject_commit = sys.argv[4]
checks = 0
failures = 0


def record(ok, label, detail=""):
    global checks, failures
    checks += 1
    if not ok:
        failures += 1
    suffix = f" ({detail})" if detail else ""
    print(f"{'PASS' if ok else 'FAIL'} | {label}{suffix}")


def git_paths(*args):
    result = subprocess.run(
        ["git", "-C", str(root), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
    )
    if result.returncode != 0:
        raise RuntimeError("git path query failed")
    return [item.decode("utf-8", "surrogateescape") for item in result.stdout.split(b"\0") if item]


def load_json(path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def schema_keywords(schema, location="$", unknown=None):
    if unknown is None:
        unknown = []
    supported = {
        "$schema", "$id", "$ref", "$defs", "title", "description", "type",
        "required", "properties", "additionalProperties", "items", "minItems",
        "uniqueItems", "minLength", "pattern", "enum", "const", "format", "minimum",
    }
    if not isinstance(schema, dict):
        return unknown
    for key in schema:
        if key not in supported:
            unknown.append(f"{location}:{key}")
    for container in ("$defs", "properties"):
        for name, child in schema.get(container, {}).items():
            schema_keywords(child, f"{location}/{container}/{name}", unknown)
    items = schema.get("items")
    if isinstance(items, dict):
        schema_keywords(items, f"{location}/items", unknown)
    additional = schema.get("additionalProperties")
    if isinstance(additional, dict):
        schema_keywords(additional, f"{location}/additionalProperties", unknown)
    return unknown


def resolve_ref(root_schema, reference):
    if not reference.startswith("#/"):
        raise ValueError("only local schema references are supported")
    node = root_schema
    for part in reference[2:].split("/"):
        node = node[part.replace("~1", "/").replace("~0", "~")]
    return node


def type_matches(instance, expected):
    mapping = {
        "object": lambda value: isinstance(value, dict),
        "array": lambda value: isinstance(value, list),
        "string": lambda value: isinstance(value, str),
        "integer": lambda value: isinstance(value, int) and not isinstance(value, bool),
        "number": lambda value: isinstance(value, (int, float)) and not isinstance(value, bool),
        "boolean": lambda value: isinstance(value, bool),
        "null": lambda value: value is None,
    }
    return mapping.get(expected, lambda _value: False)(instance)


def validate(instance, schema, root_schema, location="$", errors=None):
    if errors is None:
        errors = []
    if "$ref" in schema:
        try:
            referenced = resolve_ref(root_schema, schema["$ref"])
        except (KeyError, TypeError, ValueError):
            errors.append(f"{location}: unresolved schema reference")
            return errors
        validate(instance, referenced, root_schema, location, errors)

    expected_type = schema.get("type")
    if expected_type is not None and not type_matches(instance, expected_type):
        errors.append(f"{location}: type mismatch")
        return errors
    if "const" in schema and instance != schema["const"]:
        errors.append(f"{location}: const mismatch")
    if "enum" in schema and instance not in schema["enum"]:
        errors.append(f"{location}: enum mismatch")

    if isinstance(instance, str):
        if len(instance) < schema.get("minLength", 0):
            errors.append(f"{location}: minLength violation")
        pattern = schema.get("pattern")
        if pattern is not None and re.search(pattern, instance) is None:
            errors.append(f"{location}: pattern mismatch")
        if schema.get("format") == "date-time":
            try:
                parsed = datetime.fromisoformat(instance.replace("Z", "+00:00"))
                if parsed.tzinfo is None:
                    raise ValueError
            except ValueError:
                errors.append(f"{location}: invalid date-time")

    if isinstance(instance, (int, float)) and not isinstance(instance, bool):
        if "minimum" in schema and instance < schema["minimum"]:
            errors.append(f"{location}: minimum violation")

    if isinstance(instance, list):
        if len(instance) < schema.get("minItems", 0):
            errors.append(f"{location}: minItems violation")
        if schema.get("uniqueItems"):
            canonical = [json.dumps(item, ensure_ascii=False, sort_keys=True) for item in instance]
            if len(canonical) != len(set(canonical)):
                errors.append(f"{location}: uniqueItems violation")
        item_schema = schema.get("items")
        if isinstance(item_schema, dict):
            for index, item in enumerate(instance):
                validate(item, item_schema, root_schema, f"{location}/{index}", errors)

    if isinstance(instance, dict):
        required = schema.get("required", [])
        for key in required:
            if key not in instance:
                errors.append(f"{location}: missing required property {key}")
        properties = schema.get("properties", {})
        for key, child in properties.items():
            if key in instance:
                validate(instance[key], child, root_schema, f"{location}/{key}", errors)
        if schema.get("additionalProperties") is False:
            extras = set(instance) - set(properties)
            for key in sorted(extras):
                errors.append(f"{location}: additional property {key}")
    return errors


def path_within(candidate, allowed_patterns):
    for allowed in allowed_patterns:
        if candidate == allowed:
            return True
        if allowed.endswith("/**"):
            prefix = allowed[:-3].rstrip("/")
            if candidate == prefix or candidate.startswith(prefix + "/"):
                return True
        elif not any(char in candidate for char in "*?[") and fnmatch.fnmatchcase(candidate, allowed):
            return True
    return False


print("FrameFlow M01-H hardening evidence")
print(f"base commit:    {base_commit}")
print(f"worktree:       {worktree_state}")
print(f"formal subject commit: {formal_subject_commit}")
print(f"generated at:  {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
print("security:       findings contain counts and relative paths only; no matched value is printed")
print()

# JSON syntax across repository-owned JSON files.
try:
    json_rel_paths = sorted(set(git_paths("ls-files", "-co", "--exclude-standard", "-z", "--", "*.json")))
except RuntimeError:
    json_rel_paths = []
    record(False, "enumerate repository JSON files")
json_errors = []
json_cache = {}
for relative in json_rel_paths:
    path = root / relative
    try:
        json_cache[relative] = load_json(path)
    except (OSError, UnicodeError, json.JSONDecodeError):
        json_errors.append(relative)
record(not json_errors and bool(json_rel_paths), "JSON syntax", f"files={len(json_rel_paths)} invalid={len(json_errors)}")

# Validate every schema keyword used by the three control-plane schemas, then validate instances.
schema_dir = root / "docs/05-engineering/schemas"
schema_specs = {
    "task": load_json(schema_dir / "task-capsule.schema.json"),
    "grant": load_json(schema_dir / "capability-grant.schema.json"),
    "receipt": load_json(schema_dir / "agent-receipt.schema.json"),
}
unknown_keywords = []
for name, schema in schema_specs.items():
    unknown_keywords.extend(f"{name}:{item}" for item in schema_keywords(schema))
record(not unknown_keywords, "all JSON Schema keywords used by active schemas are implemented", f"unknown={len(unknown_keywords)}")

task_files = sorted((root / "docs/05-engineering/tasks").glob("**/*.json"))
grant_files = sorted((root / "evidence").glob("**/capability-grant.json"))
receipt_files = sorted((root / "evidence").glob("**/agent-receipt.json"))
instance_groups = (
    ("Task Capsule schema", task_files, schema_specs["task"]),
    ("Capability Grant schema", grant_files, schema_specs["grant"]),
    ("Agent Receipt schema", receipt_files, schema_specs["receipt"]),
)
for label, files, schema in instance_groups:
    invalid = 0
    for path in files:
        try:
            instance = load_json(path)
            invalid += int(bool(validate(instance, schema, schema)))
        except (OSError, UnicodeError, json.JSONDecodeError):
            invalid += 1
    record(bool(files) and invalid == 0, label, f"files={len(files)} invalid={invalid}")

m01h_receipt_path = root / "evidence/m01h/agent-receipt.json"
record(m01h_receipt_path.is_file(), "M01-H Agent Receipt is present for final hardening validation")

# Task/Grant subsets and Receipt cross references.
tasks = {}
for path in task_files:
    try:
        task = load_json(path)
        tasks[task["taskId"]] = task
    except (KeyError, OSError, UnicodeError, json.JSONDecodeError):
        pass
grants = {}
for path in grant_files:
    try:
        grant = load_json(path)
        grants[grant["grantId"]] = grant
    except (KeyError, OSError, UnicodeError, json.JSONDecodeError):
        continue
    task = tasks.get(grant.get("taskId"))
    subset_ok = task is not None
    if task is not None:
        subset_ok = subset_ok and all(path_within(item, task["readSet"]) for item in grant["files"]["read"])
        subset_ok = subset_ok and all(path_within(item, task["writeSet"]) for item in grant["files"]["write"])
        subset_ok = subset_ok and all(command in task["allowedCommands"] for command in grant["commands"])
        subset_ok = subset_ok and all(
            not grant["network"][key] or task["networkPolicy"][key]
            for key in ("allowExternal", "allowLocal", "allowRegistry")
        )
        subset_ok = subset_ok and set(grant["tools"]).issubset(set(task["toolCapabilities"]))
        approval_pairs = {(item["action"], item["approvalType"]) for item in task["approvalPoints"]}
        subset_ok = subset_ok and all(
            (item["action"], item["approvalType"]) in approval_pairs for item in grant["approvals"]
        )
    record(subset_ok, "Task/Grant least-privilege subset", path.relative_to(root).as_posix())

for path in receipt_files:
    try:
        receipt = load_json(path)
    except (OSError, UnicodeError, json.JSONDecodeError):
        continue
    task = tasks.get(receipt.get("taskId"))
    grant = grants.get(receipt.get("grantId"))
    cross_ok = task is not None and grant is not None and grant.get("taskId") == receipt.get("taskId")
    if cross_ok:
        cross_ok = all(path_within(item, grant["files"]["write"]) for item in receipt["changedPaths"])
        cross_ok = cross_ok and set(receipt["evidenceRefs"]) == {item["evidenceId"] for item in task["evidence"]}
        cross_ok = cross_ok and {item["testId"] for item in receipt["testResults"]}.issubset(set(task["testIds"]))
        cross_ok = cross_ok and {item["acceptanceId"] for item in receipt["acceptanceResults"]}.issubset(
            {item["acceptanceId"] for item in task["acceptance"]}
        )
    record(cross_ok, "Receipt Task/Grant/path/reference cross-check", path.relative_to(root).as_posix())

evidence_index_text = (root / "docs/09-delivery/evidence-index.md").read_text(encoding="utf-8")
declared_evidence_ids = {
    item["evidenceId"]
    for task in tasks.values()
    for item in task.get("evidence", [])
}
index_counts = {
    evidence_id: len(re.findall(rf"\|\s*{re.escape(evidence_id)}\s*\|", evidence_index_text))
    for evidence_id in declared_evidence_ids
}
record(
    bool(declared_evidence_ids) and all(count == 1 for count in index_counts.values()),
    "Task Evidence IDs map exactly once into evidence-index",
    f"declared={len(declared_evidence_ids)} mismatches={sum(count != 1 for count in index_counts.values())}",
)

# Evidence secret and personal-path scan. Only file names/counts are reported, never matched data.
evidence_files = sorted(path for path in (root / "evidence").glob("**/*") if path.is_file())
jwt_pattern = re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}")
token_value_pattern = re.compile(
    r'"(?:(?:access|refresh)[_-]?token)"\s*:\s*"(?!<REDACTED>")[^"]+"', re.IGNORECASE
)
bearer_pattern = re.compile(r"\bBearer\s+[A-Za-z0-9._~-]{20,}", re.IGNORECASE)
personal_path_pattern = re.compile(r"/Users/[^/\s]+")
private_key_pattern = re.compile(r"-----BEGIN (?:RSA )?PRIVATE KEY-----")
secret_files = set()
personal_path_files = set()
for path in evidence_files:
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        secret_files.add(path.relative_to(root).as_posix())
        continue
    relative = path.relative_to(root).as_posix()
    if jwt_pattern.search(text) or token_value_pattern.search(text) or bearer_pattern.search(text) or private_key_pattern.search(text):
        secret_files.add(relative)
    if personal_path_pattern.search(text):
        personal_path_files.add(relative)
record(not secret_files, "working-tree Evidence contains no plaintext credential material", f"files={len(evidence_files)} findings={len(secret_files)}")
record(not personal_path_files, "working-tree Evidence contains no personal absolute path", f"findings={len(personal_path_files)}")
print("INFO | Git history is not rewritten or certified by this task; legacy secret revocation/history cleanup remains separately gated")

legacy_text_files = [
    root / "evidence/m01/auth-flows.txt",
    root / "evidence/m01/migration.txt",
    root / "evidence/m01/mvn-verify.txt",
    root / "evidence/m01/openapi-diff.txt",
]
legacy_markers_ok = all(
    path.is_file()
    and "SUPERSEDED" in path.read_text(encoding="utf-8", errors="replace")
    and "SECURITY-REDACTED" in path.read_text(encoding="utf-8", errors="replace")
    for path in legacy_text_files
)
record(legacy_markers_ok, "legacy M01 evidence is marked SUPERSEDED/SECURITY-REDACTED")

# Historical M01 Receipt must retain the actual 90-path fact and truthful budget flag.
old_task = tasks.get("FF-M01-001", {})
old_receipt = load_json(root / "evidence/m01/agent-receipt.json")
budget = old_task.get("budget", {})
try:
    started = datetime.fromisoformat(old_receipt["startedAt"].replace("Z", "+00:00"))
    finished = datetime.fromisoformat(old_receipt["finishedAt"].replace("Z", "+00:00"))
    elapsed_minutes = (finished - started).total_seconds() / 60
except (KeyError, TypeError, ValueError):
    elapsed_minutes = float("inf")
expected_over_budget = (
    len(old_receipt.get("changedPaths", [])) > budget.get("maxFiles", 0)
    or len(old_receipt.get("commandResults", [])) > budget.get("maxCommands", 0)
    or elapsed_minutes > budget.get("maxMinutes", 0)
)
record(len(old_receipt.get("changedPaths", [])) == 90, "legacy M01 Receipt changedPaths fact", "paths=90")
record(expected_over_budget and old_receipt.get("overBudget") is True, "legacy M01 Receipt overBudget truth")
receipt_marked = any(
    "SUPERSEDED" in item and "SECURITY-REDACTED" in item for item in old_receipt.get("unknowns", [])
)
record(receipt_marked, "legacy M01 Receipt is marked SUPERSEDED/SECURITY-REDACTED")

mvn_header_command = ""
for line in (root / "evidence/m01/mvn-verify.txt").read_text(encoding="utf-8", errors="replace").splitlines():
    if line.startswith("command:"):
        mvn_header_command = line.split(":", 1)[1].strip()
        break
receipt_commands = [item.get("command") for item in old_receipt.get("commandResults", [])]
record(bool(mvn_header_command) and mvn_header_command in receipt_commands, "legacy M01 Receipt records the Maven command shown by source Evidence")
old_grant = grants.get(old_receipt.get("grantId"), {})
historically_authorized = any(
    mvn_header_command == item.get("pattern")
    or (
        item.get("policy") == "prefix"
        and mvn_header_command.startswith(str(item.get("pattern")) + " ")
    )
    for item in old_grant.get("commands", [])
)
grant_mismatch_recorded = any(
    "与原 Grant 中 exact 命令不一致" in item for item in old_receipt.get("unknowns", [])
)
record(
    bool(mvn_header_command) and not historically_authorized and grant_mismatch_recorded,
    "legacy out-of-Grant Maven command is disclosed rather than retroactively authorized",
)

# Frontend task identifiers must use FF-MxxF-nnn; legacy FF-Fn-nnn is forbidden.
normative_paths = []
for relative in git_paths("ls-files", "-co", "--exclude-standard", "-z", "--", "README.md", "docs"):
    path = root / relative
    if path.is_file() and path.suffix.lower() in {".md", ".json", ".yaml", ".yml", ".txt"}:
        normative_paths.append(path)
legacy_frontend = 0
invalid_frontend = 0
frontend_like = re.compile(r"\bFF-M[0-9]+F-[0-9]+\b")
valid_frontend = re.compile(r"^FF-M[0-9]{2}F-[0-9]{3}$")
for path in normative_paths:
    text = path.read_text(encoding="utf-8", errors="replace")
    legacy_frontend += len(re.findall(r"\bFF-F[0-9]+-[0-9]{3}\b", text))
    invalid_frontend += sum(not valid_frontend.fullmatch(item) for item in frontend_like.findall(text))
frontend_task_paths = sorted((root / "docs/05-engineering/tasks").glob("M*F/*.json"))
expected_frontend_ids = {"FF-M01F-001", "FF-M04F-001", "FF-M08F-001"}
found_frontend_ids = set()
frontend_path_ok = all(
    re.fullmatch(r"M[0-9]{2}F", path.parent.name)
    and re.fullmatch(r"FF-M[0-9]{2}F-[0-9]{3}\.json", path.name)
    for path in frontend_task_paths
)
for path in frontend_task_paths:
    try:
        found_frontend_ids.add(load_json(path).get("taskId"))
    except (OSError, UnicodeError, json.JSONDecodeError):
        frontend_path_ok = False
record(
    legacy_frontend == 0
    and invalid_frontend == 0
    and frontend_path_ok
    and expected_frontend_ids.issubset(found_frontend_ids),
    "frontend identifiers use legal FF-MxxF-nnn numbering",
    f"taskFiles={len(frontend_task_paths)}",
)

# Worktree path audit: dirty is allowed; unsafe or out-of-envelope paths are not.
dirty_paths = set()
try:
    dirty_paths.update(git_paths("diff", "--name-only", "-z"))
    dirty_paths.update(git_paths("diff", "--cached", "--name-only", "-z"))
    dirty_paths.update(git_paths("ls-files", "--others", "--exclude-standard", "-z"))
except RuntimeError:
    record(False, "enumerate worktree paths")

unsafe_paths = []
for relative in dirty_paths:
    pure = PurePosixPath(relative)
    unsafe = (
        pure.is_absolute()
        or ".." in pure.parts
        or relative == ".git"
        or relative.startswith(".git/")
        or relative == ".zcode"
        or relative.startswith(".zcode/")
        or relative == "data/jwt"
        or relative.startswith("data/jwt/")
    )
    candidate = root / relative
    if candidate.exists():
        try:
            candidate.resolve().relative_to(root)
        except ValueError:
            unsafe = True
    if unsafe:
        unsafe_paths.append(relative)
record(not unsafe_paths, "worktree paths are repository-relative and exclude protected local state", f"dirtyPaths={len(dirty_paths)} findings={len(unsafe_paths)}")

m01h_task = tasks.get("FF-M01H-001", {})
dispatcher_owned = {
    "docs/05-engineering/tasks/M01H/FF-M01H-001.json",
    "evidence/m01h/capability-grant.json",
}
outside_envelope = [
    relative
    for relative in dirty_paths
    if relative not in dispatcher_owned and not path_within(relative, m01h_task.get("writeSet", []))
]
record(not outside_envelope, "dirty worktree paths stay within M01-H write envelope or dispatcher-owned control files", f"findings={len(outside_envelope)}")
record(
    formal_subject_commit == base_commit,
    "formal subject commit is anchored; only finalization paths may be dirty",
    f"state={worktree_state}",
)

print()
print(f"RESULT: CHECKS={checks} FAILURES={failures}")
sys.exit(1 if failures else 0)
PY
CHECK_EXIT=$?

mkdir -p "$EVIDENCE_DIR"
mv "$TMP_RESULT" "$RESULT_FILE"
trap - EXIT INT TERM
exit "$CHECK_EXIT"
