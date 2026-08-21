#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Learning-first rebuild validator (AGENT_SUPPORT).

Checks the learning mainline is coherent and safe: six books, .learning control
files (mini YAML subset), git reference refs, minimal skeleton (no business
leftovers), no obvious secrets, README/AGENTS present.
"""
import io, json, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors = []
ok = []

def check(cond, msg):
    if cond:
        ok.append(msg)
    else:
        errors.append(msg)

def load_yaml(path):
    # mini parser for this project YAML subset (maps + scalars; lists ignored)
    result = {}
    try:
        with io.open(path, encoding="utf-8") as f:
            raw = f.read()
    except Exception as e:
        errors.append("cannot read %s: %s" % (path, e))
        return None
    for ln in raw.splitlines():
        s = ln.strip()
        if not s or s.startswith("#") or s.startswith("-"):
            continue
        if ":" not in s:
            continue
        key, _, val = s.partition(":")
        val = val.strip()
        if val and val not in ("[]", "{}"):
            result[key.strip()] = val.strip(chr(34)).strip(chr(39))
        else:
            result[key.strip()] = None
    return result

# 1. books
books_dir = os.path.join(ROOT, "docs", "books")
books = [f for f in os.listdir(books_dir) if f.endswith(".md")] if os.path.isdir(books_dir) else []
check(len(books) == 6, "six books present (got %d)" % len(books))
book_re = re.compile(r"^BOOK-0[1-6]-[^.]+\.md$")
check(all(book_re.match(b) for b in books), "book filenames match BOOK-01..06 pattern")

# 2. .learning control files
learning = os.path.join(ROOT, ".learning")
for f in ["roadmap.yaml", "state.yaml", "skill-matrix.yaml", "decision-policy.yaml", "evidence-index.yaml"]:
    p = os.path.join(learning, f)
    check(os.path.isfile(p), ".learning/%s exists" % f)
    if os.path.isfile(p):
        data = load_yaml(p)
        check(data is not None, ".learning/%s parses (mini yaml)" % f)
        if f == "state.yaml" and data:
            check(data.get("status") == "LEARNING_REBUILD_READY", "state.status == LEARNING_REBUILD_READY")
            check(data.get("referenceTag") == "frameflow-select-agent-mvp-v1.0.0", "state.referenceTag matches")
p = os.path.join(learning, "task.schema.json")
check(os.path.isfile(p), ".learning/task.schema.json exists")
if os.path.isfile(p):
    try:
        with io.open(p, encoding="utf-8") as fh:
            json.load(fh)
        check(True, ".learning/task.schema.json is valid JSON")
    except Exception as e:
        check(False, ".learning/task.schema.json invalid: %s" % e)
tasks = os.path.join(learning, "tasks")
if os.path.isdir(tasks):
    names = os.listdir(tasks)
    check(any(n.startswith("LR1-H001") for n in names), "first human task LR1-H001 exists")
    check(any(n.startswith("LR2-H001") for n in names), "second human task LR2-H001 exists")
check(os.path.isfile(os.path.join(learning, "templates", "task-template.yaml")), "task template exists")

# 3. git refs
def git(args):
    return subprocess.run(["git", "-C", ROOT] + args, capture_output=True, text=True).stdout.strip()
ref = git(["rev-parse", "--verify", "refs/tags/frameflow-select-agent-mvp-v1.0.0"])
check(ref == "c89355046ebaae1573424031014790e877932806", "reference tag points to verified MVP commit")
ref = git(["rev-parse", "--verify", "archive/frameflow-select-agent-mvp-v1"])
check(ref == "c89355046ebaae1573424031014790e877932806", "reference branch points to verified MVP commit")
check(git(["branch", "--show-current"]) == "frameflow-select/learning-main", "active branch is learning-main")

# 4. minimal skeleton: no business leftovers tracked
tracked = git(["ls-files"])
marks = ["BatchEngineController", "SelectionController", "FakeSemanticProvider", "SingleCandidateSliceE2ETest", "ArchUnit", "MybatisPlusApplicationContextAware"]
for mk in marks:
    check(not any(mk in t for t in tracked.splitlines() if t.startswith(("frameflow-modules", "frameflow-app", "frameflow-web", "frameflow-ai-worker"))), "no business leftover: %s" % mk)

# 5. secrets scan on tracked non-archive files
secret_re = re.compile(r"(-----BEGIN [A-Z ]*PRIVATE KEY-----|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|password\s*[:=]\s*\S{6,}|FRAMEFLOW_REAL_PROVIDER_API_KEY\s*=\s*\S+)")
hits = []
for t in tracked.splitlines():
    if t.startswith("docs/archive/"):
        continue
    p = os.path.join(ROOT, t)
    if not os.path.isfile(p):
        continue
    try:
        txt = io.open(p, encoding="utf-8", errors="ignore").read(4000)
    except Exception:
        continue
    if secret_re.search(txt):
        hits.append(t)
check(not hits, "no obvious secrets in tracked non-archive files (hits=%s)" % hits[:3])

# 6. top-level docs
check(os.path.isfile(os.path.join(ROOT, "README.md")), "README.md exists")
check(os.path.isfile(os.path.join(ROOT, "AGENTS.md")), "AGENTS.md exists")
check(os.path.isfile(os.path.join(ROOT, "scripts", "validate_learning.py")), "validator script exists")

print("LEARNING_VALIDATION: %d ok, %d errors" % (len(ok), len(errors)))
for e in errors:
    print("  ERROR:", e)
sys.exit(1 if errors else 0)
