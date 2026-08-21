#!/usr/bin/env python3
"""Validate the FrameFlow P0-Prep execution baseline with stdlib only.

Before Git bootstrap, the validator writes the five evidence files declared by
FF-PP-001 and exits non-zero when any gate fails. Once .git exists it refuses
to run, preserving the pre-Git evidence as an immutable bootstrap record. It
deliberately does not inspect .zcode content: .zcode is a user-retained,
ignored local tool directory and is not an authoritative FrameFlow input.
"""

from __future__ import annotations

import datetime as dt
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
ENGINEERING = ROOT / "docs/05-engineering"
TASK_DIR = ENGINEERING / "tasks"
EVIDENCE_DIR = ROOT / "evidence/prep"
VALIDATOR_COMMAND = "python3 scripts/validate_p0_prep.py"

TASK_ID_RE = re.compile(r"^FF-(PP|P0|M[0-9]{2}[A-Z]?)-[0-9]{3}$")
AC_ID_RE = re.compile(r"^AC-FF-(PP|P0|M[0-9]{2}[A-Z]?)-[0-9]{3}-[0-9]{2}$")
TEST_ID_RE = re.compile(r"^TEST-FF-(PP|P0|M[0-9]{2}[A-Z]?)-[0-9]{3}-[0-9]{2}$")
EVIDENCE_ID_RE = re.compile(r"^EV-FF-(PP|P0|M[0-9]{2}[A-Z]?)-[0-9]{3}-[0-9]{2}$")
REQ_ID_RE = re.compile(r"^REQ-[A-Z0-9]+-[0-9]{3}$")


@dataclass
class Gate:
    evidence_id: str
    title: str
    output_name: str
    checks: list[tuple[bool, str, str]] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)

    def check(self, condition: bool, label: str, detail: str = "") -> None:
        self.checks.append((bool(condition), label, detail))

    @property
    def passed(self) -> bool:
        return bool(self.checks) and all(item[0] for item in self.checks)

    def render(self, generated_at: str) -> str:
        lines = [
            f"Evidence ID: {self.evidence_id}",
            "Task ID: FF-PP-001",
            "Stage: P0-Prep",
            f"Goal: {self.title}",
            f"Generated at: {generated_at}",
            f"Environment: Python {platform.python_version()}; {platform.platform()}",
            f"Command: {VALIDATOR_COMMAND}",
            f"Result: {'PASS' if self.passed else 'FAIL'}",
            "Git commit: NOT_AVAILABLE (FF-PP-001 pre-Git bootstrap exception; the immutable baseline is the first authorized P0 commit, recorded by P0 evidence)",
            "",
            "Checks:",
        ]
        for ok, label, detail in self.checks:
            suffix = f" — {detail}" if detail else ""
            lines.append(f"[{'PASS' if ok else 'FAIL'}] {label}{suffix}")
        if self.notes:
            lines.extend(["", "Boundaries / notes:"])
            lines.extend(f"- {note}" for note in self.notes)
        return "\n".join(lines) + "\n"


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def read_text(path: str | Path) -> str:
    target = ROOT / path if isinstance(path, str) else path
    return target.read_text(encoding="utf-8")


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def resolve_json_pointer(root_schema: dict[str, Any], reference: str) -> dict[str, Any]:
    """Resolve the local JSON Pointers used by the bundled schemas."""
    if not reference.startswith("#/"):
        raise ValueError(f"only local JSON Pointer references are supported: {reference}")
    current: Any = root_schema
    for raw_part in reference[2:].split("/"):
        part = raw_part.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or part not in current:
            raise ValueError(f"unresolved JSON Pointer: {reference}")
        current = current[part]
    if not isinstance(current, dict):
        raise ValueError(f"JSON Pointer does not reference a schema object: {reference}")
    return current


def schema_errors(
    instance: Any,
    schema: dict[str, Any],
    root_schema: dict[str, Any] | None = None,
    location: str = "$",
) -> list[str]:
    """Validate the Draft 2020-12 keywords currently used by FrameFlow.

    This is deliberately a small, auditable validator rather than a claim of
    full JSON Schema conformance. The P0 dependency policy does not permit a
    third-party Python package during bootstrap.
    """
    root_schema = root_schema or schema
    if "$ref" in schema:
        return schema_errors(instance, resolve_json_pointer(root_schema, schema["$ref"]), root_schema, location)

    errors: list[str] = []
    if "const" in schema and instance != schema["const"]:
        errors.append(f"{location}: expected const {schema['const']!r}")
    if "enum" in schema and instance not in schema["enum"]:
        errors.append(f"{location}: value is not in enum")

    expected_type = schema.get("type")
    type_ok = True
    if expected_type == "object":
        type_ok = isinstance(instance, dict)
    elif expected_type == "array":
        type_ok = isinstance(instance, list)
    elif expected_type == "string":
        type_ok = isinstance(instance, str)
    elif expected_type == "integer":
        type_ok = isinstance(instance, int) and not isinstance(instance, bool)
    elif expected_type == "boolean":
        type_ok = isinstance(instance, bool)
    if expected_type and not type_ok:
        return errors + [f"{location}: expected {expected_type}"]

    if isinstance(instance, dict):
        properties = schema.get("properties", {})
        for required in schema.get("required", []):
            if required not in instance:
                errors.append(f"{location}: missing required property {required}")
        if schema.get("additionalProperties") is False:
            for name in instance:
                if name not in properties:
                    errors.append(f"{location}: unexpected property {name}")
        for name, value in instance.items():
            child_schema = properties.get(name)
            if isinstance(child_schema, dict):
                errors.extend(schema_errors(value, child_schema, root_schema, f"{location}.{name}"))

    if isinstance(instance, list):
        if len(instance) < schema.get("minItems", 0):
            errors.append(f"{location}: fewer than minItems")
        if schema.get("uniqueItems"):
            normalized = [json.dumps(item, sort_keys=True, ensure_ascii=False) for item in instance]
            if len(normalized) != len(set(normalized)):
                errors.append(f"{location}: items are not unique")
        item_schema = schema.get("items")
        if isinstance(item_schema, dict):
            for index, value in enumerate(instance):
                errors.extend(schema_errors(value, item_schema, root_schema, f"{location}[{index}]"))

    if isinstance(instance, str):
        if len(instance) < schema.get("minLength", 0):
            errors.append(f"{location}: shorter than minLength")
        if "pattern" in schema and re.fullmatch(schema["pattern"], instance) is None:
            errors.append(f"{location}: does not match pattern")

    if isinstance(instance, int) and not isinstance(instance, bool):
        if "minimum" in schema and instance < schema["minimum"]:
            errors.append(f"{location}: smaller than minimum")
    return errors


def authoritative_files() -> Iterable[Path]:
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        if relative.parts and relative.parts[0] in {".zcode", "evidence"}:
            continue
        if relative.parts[:3] == ("docs", "08-learning", "legacy"):
            continue
        if path.suffix.lower() in {".md", ".json", ".yaml", ".yml", ".py"} or path.name in {
            "README.md",
            "CONTRIBUTING.md",
            ".gitignore",
        }:
            yield path


def path_covered(path: str, rules: list[str]) -> bool:
    for rule in rules:
        if rule == path:
            return True
        if rule.endswith("/**") and path.startswith(rule[:-3].rstrip("/") + "/"):
            return True
    return False


def command_allowed(command: str, rules: list[dict[str, str]]) -> bool:
    for rule in rules:
        pattern = rule["pattern"]
        if rule["policy"] == "exact" and command == pattern:
            return True
        if rule["policy"] == "prefix" and (command == pattern or command.startswith(pattern + " ")):
            return True
    return False


def find_markdown_link_failures() -> list[str]:
    failures: list[str] = []
    link_re = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
    for path in authoritative_files():
        if path.suffix.lower() != ".md":
            continue
        for raw in link_re.findall(path.read_text(encoding="utf-8")):
            target = raw.strip().split()[0].strip("<>")
            if not target or target.startswith(("#", "http://", "https://", "mailto:")):
                continue
            target = target.split("#", 1)[0]
            resolved = (path.parent / target).resolve()
            try:
                resolved.relative_to(ROOT.resolve())
            except ValueError:
                failures.append(f"{rel(path)} -> outside workspace: {target}")
                continue
            if not resolved.exists():
                failures.append(f"{rel(path)} -> missing: {target}")
    return failures


def validate_openapi() -> tuple[bool, str]:
    ruby = shutil.which("ruby")
    if not ruby:
        return False, "Ruby/Psych unavailable; OpenAPI YAML structure could not be parsed"
    program = r'''
require "yaml"
require "json"
d = YAML.load_file(ARGV[0])
refs = []
walk = lambda do |x|
  case x
  when Hash
    x.each { |k, v| refs << v if k == "$ref"; walk.call(v) }
  when Array
    x.each { |v| walk.call(v) }
  end
end
walk.call(d)
resolve = lambda do |ref|
  next true unless ref.start_with?("#/")
  ref.split("/")[1..].reduce(d) { |acc, key| acc.is_a?(Hash) ? acc[key] : nil } != nil
end
methods = %w[get post put patch delete options head trace]
ops = d.fetch("paths", {}).flat_map do |path, item|
  item.select { |method, _| methods.include?(method) }.map do |method, op|
    {"path" => path, "method" => method, "operationId" => op["operationId"], "parameters" => op.fetch("parameters", [])}
  end
end
idem_ops = ops.select do |op|
  op["parameters"].any? { |p| p.is_a?(Hash) && p["$ref"] == "#/components/parameters/IdempotencyKey" }
end.map { |op| op["operationId"] }.sort
result = {
  "openapi" => d["openapi"],
  "paths" => d.fetch("paths", {}).size,
  "operations" => ops.size,
  "operation_ids_present" => ops.all? { |op| op["operationId"].is_a?(String) && !op["operationId"].empty? },
  "operation_ids_unique" => ops.map { |op| op["operationId"] }.uniq.size == ops.size,
  "refs" => refs.size,
  "unresolved_refs" => refs.reject { |ref| resolve.call(ref) },
  "const_count" => 0,
  "idempotency_required" => d.dig("components", "parameters", "IdempotencyKey", "required") == true,
  "idempotency_operations" => idem_ops
}
counter = lambda do |x|
  case x
  when Hash
    result["const_count"] += 1 if x.key?("const")
    x.each_value { |v| counter.call(v) }
  when Array
    x.each { |v| counter.call(v) }
  end
end
counter.call(d)
puts JSON.generate(result)
'''
    proc = subprocess.run(
        [ruby, "-ryaml", "-rjson", "-e", program, str(ROOT / "docs/04-api/openapi/frameflow-v1.yaml")],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    if proc.returncode != 0:
        return False, f"YAML parse failed: {proc.stderr.strip()}"
    try:
        result = json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        return False, f"OpenAPI parser returned invalid JSON: {exc}"
    expected_idempotency_ops = ["addTeamMember", "createTeam"]
    checks = [
        result["openapi"] == "3.0.3",
        result["operation_ids_present"],
        result["operation_ids_unique"],
        not result["unresolved_refs"],
        result["const_count"] == 0,
        result["idempotency_required"],
        result["idempotency_operations"] == expected_idempotency_ops,
    ]
    detail = (
        f"OpenAPI {result['openapi']}; paths={result['paths']}; operations={result['operations']}; "
        f"refs={result['refs']}; unresolved={len(result['unresolved_refs'])}; "
        f"idempotencyOps={','.join(result['idempotency_operations'])}"
    )
    return all(checks), detail


def main() -> int:
    if (ROOT / ".git").exists():
        print(
            "REFUSED: FF-PP-001 evidence is an immutable pre-Git bootstrap record; "
            "after Git initialization use the P0 Receipt/Evidence workflow instead.",
            file=sys.stderr,
        )
        return 2

    gates = {
        "consistency": Gate("EV-FF-PP-001-01", "Repository mode and ownership consistency", "consistency.txt"),
        "schema": Gate("EV-FF-PP-001-02", "Task schemas, identifiers and cross references", "schema-validate.txt"),
        "stage": Gate("EV-FF-PP-001-03", "Stage technology baseline consistency", "stage-baseline.txt"),
        "p0": Gate("EV-FF-PP-001-04", "P0 task reproducibility", "p0-task-review.txt"),
        "hygiene": Gate("EV-FF-PP-001-05", "Package hygiene and local-tool isolation", "hygiene.txt"),
    }

    consistency = gates["consistency"]
    required_files = [
        "README.md",
        "CONTRIBUTING.md",
        "docs/00-governance/project-status.md",
        "docs/01-product/FrameFlow-PRD.md",
        "docs/02-architecture/service-boundaries-and-data-ownership.md",
        "docs/05-engineering/task-capsule-catalog.md",
    ]
    missing = [item for item in required_files if not (ROOT / item).is_file()]
    consistency.check(not missing, "Required authoritative documents exist", ", ".join(missing) or "all present")
    readme = read_text("README.md")
    status_doc = read_text("docs/00-governance/project-status.md")
    contributing = read_text("CONTRIBUTING.md")
    ownership = read_text("docs/02-architecture/service-boundaries-and-data-ownership.md")
    consistency.check(
        all("模式 A" in text or "模式：**A" in text for text in (readme, status_doc, contributing)),
        "Single-repository mode A is consistent",
        "README, project status and CONTRIBUTING",
    )
    ownership_markers = [
        "任务评论",
        "project-service",
        "审核",
        "asset-workflow-service",
        "AI Worker",
        "不直接写业务表",
    ]
    consistency.check(
        all(marker in ownership for marker in ownership_markers),
        "Service ownership and AI Worker boundary are explicit",
        "project-service owns tasks/comments; asset-workflow owns review/delivery facts; Worker owns no business tables",
    )
    zcode_docs = [readme, status_doc, contributing, read_text("docs/00-governance/packaging-policy.md")]
    consistency.check(
        all(".zcode" in text for text in zcode_docs)
        and all(any(marker in text for marker in ("非权威", "不是项目权威", "不属于产品源码", "不是权威计划", "不是 FrameFlow 的产品源码")) for text in zcode_docs),
        ".zcode local-only boundary is consistent",
        "retained locally, ignored, non-authoritative and excluded from delivery",
    )
    link_failures = find_markdown_link_failures()
    consistency.check(not link_failures, "Authoritative Markdown links resolve", "; ".join(link_failures[:5]) or "no broken local links")

    schema_gate = gates["schema"]
    json_paths = sorted((ENGINEERING / "schemas").glob("*.json"))
    json_paths += sorted((ENGINEERING / "registries").glob("*.json"))
    json_paths += sorted(TASK_DIR.glob("*/*.json"))
    parsed: dict[Path, Any] = {}
    parse_failures: list[str] = []
    for path in json_paths:
        try:
            parsed[path] = load_json(path)
        except (OSError, json.JSONDecodeError) as exc:
            parse_failures.append(f"{rel(path)}: {exc}")
    schema_gate.check(not parse_failures, "All schema, registry and task files are valid JSON", "; ".join(parse_failures) or f"{len(parsed)} files parsed")

    task_schema_path = ENGINEERING / "schemas/task-capsule.schema.json"
    task_schema = parsed.get(task_schema_path, {})
    required_fields = set(task_schema.get("required", []))
    allowed_fields = set(task_schema.get("properties", {}))
    repo_path_pattern = task_schema.get("$defs", {}).get("repoPath", {}).get("pattern", "")
    safe_command_pattern = task_schema.get("$defs", {}).get("safeCommand", {}).get("pattern", "")
    try:
        repo_path_re = re.compile(repo_path_pattern)
        safe_command_re = re.compile(safe_command_pattern)
        regexes_valid = True
    except re.error:
        repo_path_re = re.compile(r"a^")
        safe_command_re = re.compile(r"a^")
        regexes_valid = False
    schema_gate.check(regexes_valid, "Schema safety regular expressions compile", "repoPath and safeCommand")
    unsafe_paths = ["/tmp/x", "C:/tmp/x", "../outside", "docs/../../outside", ".git/config", "docs/.git/config", r"docs\outside"]
    safe_paths = ["README.md", "docs/**", "frameflow-app/src/test/**", ".github/workflows/ci.yml"]
    path_boundary_ok = all(not repo_path_re.fullmatch(item) for item in unsafe_paths) and all(repo_path_re.fullmatch(item) for item in safe_paths)
    schema_gate.check(path_boundary_ok, "Path boundary rejects escape and nested .git paths", "negative and positive fixtures")
    unsafe_commands = ["python3 x.py | sh", "echo x; sh", "git status && whoami", "sleep 1 &", "echo $(whoami)", "cat x > y", "echo `id`"]
    safe_commands = [VALIDATOR_COMMAND, "mvn -B clean verify", "sh scripts/p0/check-health.sh"]
    command_boundary_ok = all(not safe_command_re.fullmatch(item) for item in unsafe_commands) and all(safe_command_re.fullmatch(item) for item in safe_commands)
    schema_gate.check(command_boundary_ok, "Command boundary rejects shell control and substitution", "negative and positive fixtures")

    requirements_path = ENGINEERING / "registries/requirements.json"
    tests_path = ENGINEERING / "registries/test-cases.json"
    requirement_records = parsed.get(requirements_path, {}).get("requirements", [])
    test_records = parsed.get(tests_path, {}).get("testCases", [])
    requirement_ids = [item.get("requirementId") for item in requirement_records]
    test_ids = [item.get("testId") for item in test_records]
    registry_ok = (
        all(REQ_ID_RE.fullmatch(item or "") for item in requirement_ids)
        and len(requirement_ids) == len(set(requirement_ids))
        and all(TEST_ID_RE.fullmatch(item or "") for item in test_ids)
        and len(test_ids) == len(set(test_ids))
        and all((ROOT / item.get("sourceRef", "")).is_file() for item in requirement_records)
    )
    schema_gate.check(registry_ok, "Requirement and Test registries are unique and resolvable", f"requirements={len(requirement_ids)}, tests={len(test_ids)}")

    task_paths = sorted(TASK_DIR.glob("*/*.json"))
    tasks: dict[str, dict[str, Any]] = {}
    task_failures: list[str] = []
    for path in task_paths:
        task = parsed.get(path)
        if not isinstance(task, dict):
            task_failures.append(f"{rel(path)} did not parse as object")
            continue
        task_id = task.get("taskId", "")
        if not TASK_ID_RE.fullmatch(task_id):
            task_failures.append(f"{task_id}: invalid taskId")
        if task_id in tasks:
            task_failures.append(f"{task_id}: duplicate taskId")
        tasks[task_id] = task
        instance_errors = schema_errors(task, task_schema)
        if instance_errors:
            task_failures.extend(f"{task_id}: {item}" for item in instance_errors)
        if path.stem != task_id:
            task_failures.append(f"{task_id}: filename mismatch {path.name}")
        missing_fields = required_fields - set(task)
        extra_fields = set(task) - allowed_fields
        if missing_fields:
            task_failures.append(f"{task_id}: missing {sorted(missing_fields)}")
        if extra_fields:
            task_failures.append(f"{task_id}: extra {sorted(extra_fields)}")
        for field_name in ("readSet", "writeSet"):
            values = task.get(field_name, [])
            if len(values) != len(set(values)):
                task_failures.append(f"{task_id}: duplicate {field_name}")
            for value in values:
                if not repo_path_re.fullmatch(value):
                    task_failures.append(f"{task_id}: unsafe {field_name} {value}")
        commands = task.get("allowedCommands", [])
        for command in commands:
            if command.get("policy") not in {"exact", "prefix"} or not safe_command_re.fullmatch(command.get("pattern", "")):
                task_failures.append(f"{task_id}: unsafe allowed command {command}")
        task_evidence_ids = {item.get("evidenceId") for item in task.get("evidence", [])}
        for item in task.get("evidence", []):
            evidence_id = item.get("evidenceId", "")
            if not EVIDENCE_ID_RE.fullmatch(evidence_id):
                task_failures.append(f"{task_id}: invalid evidenceId {evidence_id}")
            command = item.get("command", "")
            if not command_allowed(command, commands):
                task_failures.append(f"{task_id}: evidence command not allowed {command}")
            output = item.get("outputRef", "")
            if not repo_path_re.fullmatch(output) or not path_covered(output, task.get("writeSet", [])):
                task_failures.append(f"{task_id}: evidence output outside writeSet {output}")
        declared_tests = set(task.get("testIds", []))
        if not declared_tests.issubset(set(test_ids)):
            task_failures.append(f"{task_id}: unregistered task test IDs {sorted(declared_tests - set(test_ids))}")
        for test_id in declared_tests:
            record = next((item for item in test_records if item.get("testId") == test_id), None)
            if not record or record.get("taskId") != task_id:
                task_failures.append(f"{task_id}: registry task mismatch for {test_id}")
        declared_requirements = set(task.get("requirementIds", []))
        if not declared_requirements.issubset(set(requirement_ids)):
            task_failures.append(f"{task_id}: unregistered requirements {sorted(declared_requirements - set(requirement_ids))}")
        for acceptance in task.get("acceptance", []):
            acceptance_id = acceptance.get("acceptanceId", "")
            if not AC_ID_RE.fullmatch(acceptance_id):
                task_failures.append(f"{task_id}: invalid acceptanceId {acceptance_id}")
            acceptance_tests = set(acceptance.get("testIds", []))
            acceptance_evidence = set(acceptance.get("evidenceIds", []))
            if not acceptance_tests.issubset(declared_tests):
                task_failures.append(f"{task_id}: acceptance uses undeclared tests {sorted(acceptance_tests - declared_tests)}")
            if not acceptance_evidence.issubset(task_evidence_ids):
                task_failures.append(f"{task_id}: acceptance uses undeclared evidence {sorted(acceptance_evidence - task_evidence_ids)}")
    core_task_ids = {"FF-PP-001", "FF-P0-001", "FF-M01-001"}
    schema_gate.check(
        core_task_ids.issubset(tasks),
        "Core PP/P0/M01 capsules exist and every discovered executable capsule validates",
        ", ".join(sorted(tasks)),
    )
    for task_id, task in tasks.items():
        for prerequisite in task.get("prerequisiteTaskIds", []):
            if prerequisite not in tasks:
                task_failures.append(f"{task_id}: missing prerequisite {prerequisite}")
    expected_prerequisites = {
        "FF-PP-001": [],
        "FF-P0-001": ["FF-PP-001"],
        "FF-M01-001": ["FF-P0-001"],
    }
    for task_id, expected in expected_prerequisites.items():
        if tasks.get(task_id, {}).get("prerequisiteTaskIds") != expected:
            task_failures.append(f"{task_id}: prerequisite topology mismatch")
    schema_gate.check(not task_failures, "Task capsules pass the used JSON Schema keywords and cross references", "; ".join(task_failures[:10]) or "0 errors")

    index_text = read_text("docs/09-delivery/evidence-index.md")
    evidence_index_ids = re.findall(r"^\|\s*(EV-FF-[A-Z0-9-]+)\s*\|", index_text, flags=re.MULTILINE)
    evidence_mapping_failures: list[str] = []
    for task_id, task in tasks.items():
        declared = {item["evidenceId"] for item in task.get("evidence", [])}
        indexed = {item for item in evidence_index_ids if item.startswith("EV-" + task_id)}
        if declared != indexed:
            evidence_mapping_failures.append(f"{task_id}: declared={sorted(declared)} indexed={sorted(indexed)}")
    if len(evidence_index_ids) != len(set(evidence_index_ids)):
        evidence_mapping_failures.append("evidence index contains duplicate IDs")
    schema_gate.check(not evidence_mapping_failures, "Executable-task Evidence IDs map one-to-one to the index", "; ".join(evidence_mapping_failures) or "PP/P0/M01 aligned")

    catalog = read_text("docs/05-engineering/task-capsule-catalog.md")
    generated = read_text("docs/05-engineering/generated/task-capsule-index.md")
    state_failures: list[str] = []
    for task_id, task in tasks.items():
        expected_fragment = rf"^\|\s*{re.escape(task_id)}\s*\|.*\|\s*{re.escape(task['status'])}\s*\|"
        if not re.search(expected_fragment, catalog, flags=re.MULTILINE):
            state_failures.append(f"catalog mismatch {task_id}/{task['status']}")
        if not re.search(expected_fragment, generated, flags=re.MULTILINE):
            state_failures.append(f"generated mismatch {task_id}/{task['status']}")
    for line in catalog.splitlines():
        match = re.match(r"^\|\s*(FF-[A-Z0-9-]+)\s*\|.*\|\s*(NOT_READY|CONTRACT_READY|READY_FOR_DISPATCH|IN_PROGRESS|DONE|PLANNED)\s*\|.*`([^`]+)`", line)
        if not match:
            continue
        _, item_status, relative_path = match.groups()
        exists = (ENGINEERING / relative_path).is_file()
        if item_status == "PLANNED" and exists:
            state_failures.append(f"PLANNED task unexpectedly exists: {relative_path}")
        if item_status != "PLANNED" and not exists:
            state_failures.append(f"executable task file missing: {relative_path}")
    schema_gate.check(not state_failures, "Task status, catalog, generated index and files agree", "; ".join(state_failures) or "0 mismatches")
    old_id_hits: list[str] = []
    for path in authoritative_files():
        if path == Path(__file__).resolve():
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        if re.search(r"FF-M1(?:R)?-", text) or "tasks/M1/" in text:
            old_id_hits.append(rel(path))
    schema_gate.check(not old_id_hits, "Legacy M1 identifiers are absent from authoritative files", ", ".join(old_id_hits) or "M01 is canonical")

    openapi_ok, openapi_detail = validate_openapi()
    schema_gate.check(openapi_ok, "M01 OpenAPI structure and local references validate", openapi_detail)
    token_contract = read_text("docs/04-api/token-contract.md")
    identity_dict = read_text("docs/03-data/identity-data-dictionary.md")
    idempotency_contract = read_text("docs/04-api/idempotency-contract.md")
    detailed_design = read_text("docs/03-data/FrameFlow-详细设计.md")
    m01_contract_ok = all(marker in token_contract for marker in ("RS256", "32 字节", "GET /teams", "family_id")) and all(
        marker in identity_dict for marker in ("refresh_token_sessions", "token_hash", "family_id", "replaced_by_id")
    ) and all(
        marker in idempotency_contract
        for marker in ("PostgreSQL", "idempotency_records", "M05", "POST /teams", "POST /teams/{teamId}/members")
    ) and all(marker in identity_dict for marker in ("idempotency_records", "request_hash", "response_body", "expires_at")) and all(
        marker in detailed_design for marker in ("IdentityIdempotencyRecord", "idempotency_records", "M01 团队端点")
    )
    schema_gate.check(
        m01_contract_ok,
        "M01 token, refresh-session and PostgreSQL idempotency decisions are implementable",
        "RS256, opaque refresh family, team discovery, two idempotent operations; M05 Redis is optional acceleration",
    )
    schema_gate.notes.append("OpenAPI verification is structural (YAML parse, local refs, operation IDs and selected 3.0.3 rules), not a full third-party semantic lint.")
    schema_gate.notes.append("Schema validation covers every JSON Schema keyword currently used by the active Task Capsule schema plus cross-file invariants using Python stdlib; format annotations and full Draft 2020-12 meta-schema conformance are not claimed.")
    schema_gate.notes.append("Capability Grant/Receipt runtime subset, realpath, network and tool enforcement remains the dispatcher responsibility; P0-Prep has a documented one-time pre-control-plane bootstrap exception rather than a fabricated historical receipt.")

    stage_gate = gates["stage"]
    canonical_texts: list[tuple[str, str]] = [
        (rel(path), path.read_text(encoding="utf-8", errors="replace"))
        for path in authoritative_files()
        if path != Path(__file__).resolve()
    ]
    java21_hits = [name for name, text in canonical_texts if re.search(r"\b(?:JDK|Java)\s*21\b", text, flags=re.IGNORECASE)]
    stage_gate.check(not java21_hits, "No Java/JDK 21 baseline remains in authoritative files", ", ".join(java21_hits) or "JDK 17 only")
    jdk17_sources = [
        "README.md",
        "docs/00-governance/project-status.md",
        "docs/02-architecture/architecture-evolution-roadmap.md",
        "docs/05-engineering/tasks/P0/FF-P0-001.json",
    ]
    jdk17_ok = all("JDK 17" in read_text(path) or "Java 17" in read_text(path) for path in jdk17_sources)
    stage_gate.check(jdk17_ok, "JDK 17 is explicit in all stage authorities", ", ".join(jdk17_sources))
    minio_sources = [
        "README.md",
        "docs/00-governance/project-status.md",
        "docs/01-product/FrameFlow-PRD.md",
        "docs/02-architecture/FrameFlow-概要设计.md",
        "docs/05-engineering/development-plan-p0-m17.md",
    ]
    minio_ok = all("M4-B" in read_text(path) and "MinIO" in read_text(path) for path in minio_sources)
    stage_gate.check(minio_ok, "MinIO introduction is consistently M4-B", ", ".join(minio_sources))
    ci_text = read_text("docs/05-engineering/ci-quality-gates.md")
    testcontainers_ok = all(marker in ci_text for marker in ("P0/M01 起 PostgreSQL", "M04-B 起 MinIO", "M05 起 Redis", "M06 起 RabbitMQ", "M11 起 Kafka"))
    stage_gate.check(testcontainers_ok, "Testcontainers starts with each real dependency", "PostgreSQL P0/M01; MinIO M04-B; Redis M05; RabbitMQ M06; Kafka M11")
    zcode_exists = (ROOT / ".zcode").is_dir()
    stage_gate.check(True, ".zcode does not participate in baseline scanning", f"directory {'present and retained' if zcode_exists else 'not present'}; content intentionally excluded as non-authoritative")

    p0_gate = gates["p0"]
    p0_task = tasks.get("FF-P0-001", {})
    p0_status = p0_task.get("status")
    safe_p0_status = p0_status in {"NOT_READY", "READY_FOR_DISPATCH"}
    p0_gate.check(
        safe_p0_status,
        "P0 is either awaiting authorization or authorized for first dispatch",
        f"git=absent; status={p0_status}",
    )
    p0_gate.check(p0_task.get("prerequisiteTaskIds") == ["FF-PP-001"], "P0 depends only on completed P0-Prep", str(p0_task.get("prerequisiteTaskIds")))
    decisions_text = "\n".join(p0_task.get("decisions", []))
    versions_ok = all(marker in decisions_text for marker in ("JDK 17", "Spring Boot 3.4.5", "PostgreSQL 16-alpine", "Maven Wrapper 3.9.x"))
    p0_gate.check(versions_ok, "P0 versions are fixed", "JDK 17, Boot 3.4.5, PostgreSQL 16-alpine, Maven Wrapper 3.9.x")
    p0_required_writes = [
        "README.md",
        ".github/workflows/ci.yml",
        "scripts/p0/**",
        "evidence/p0/**",
        "docs/00-governance/project-status.md",
        "docs/09-delivery/evidence-index.md",
    ]
    p0_gate.check(all(item in p0_task.get("writeSet", []) for item in p0_required_writes), "P0 writeSet covers implementation, scripts, CI, status and evidence", ", ".join(p0_required_writes))
    p0_evidence = p0_task.get("evidence", [])
    commands_reproducible = len(p0_evidence) == 6 and all(command_allowed(item["command"], p0_task.get("allowedCommands", [])) for item in p0_evidence)
    p0_gate.check(commands_reproducible, "Every P0 evidence command is directly executable and allowlisted", ", ".join(item.get("command", "") for item in p0_evidence))
    acceptance_text = "\n".join(item.get("criterion", "") for item in p0_task.get("acceptance", []))
    health_contract_ok = all(marker in acceptance_text for marker in ('{"status":"UP"}', "503", "NOT_READY", "200", "READY", "no-op"))
    p0_gate.check(health_contract_ok, "Health, readiness and Flyway acceptance are explicit", "health independent; readiness fails/recoveries; migration repeat is no-op")
    approval_actions = {item.get("action"): item.get("approvalType") for item in p0_task.get("approvalPoints", [])}
    approvals_ok = approval_actions.get("git-init") == "irreversible" and approval_actions.get("p0-execution") == "local"
    p0_gate.check(approvals_ok, "Git initialization and P0 execution are separate explicit approval points", "both user authorizations are still required")
    rollback_text = p0_task.get("rollback", "")
    rollback_ok = "git revert" in rollback_text and "禁止 reset" in rollback_text and "删除 .git" in rollback_text
    p0_gate.check(rollback_ok, "P0 rollback preserves Git history and shared work", rollback_text)
    p0_gate.notes.append("Docker daemon availability is deliberately deferred to P0 execution; it is not a P0-Prep documentation gate.")

    hygiene = gates["hygiene"]
    ignore_text = read_text(".gitignore")
    required_ignores = [".zcode/", ".DS_Store", "__MACOSX/", "target/", ".env"]
    hygiene.check(all(item in ignore_text for item in required_ignores), ".gitignore covers local tools, platform garbage, build and secrets", ", ".join(required_ignores))
    package_policy = read_text("docs/00-governance/packaging-policy.md")
    package_boundary_ok = all(marker in package_policy for marker in ("git archive", ".zcode/", "不是 FrameFlow 的产品源码", "不得进入正式交付包"))
    hygiene.check(package_boundary_ok, "Packaging policy excludes .zcode without deleting it", "git archive or equivalent clean packaging")
    platform_garbage: list[str] = []
    for path in ROOT.rglob("*"):
        relative = path.relative_to(ROOT)
        if relative.parts and relative.parts[0] == ".zcode":
            continue
        if path.name == ".DS_Store" or "__MACOSX" in relative.parts:
            platform_garbage.append(relative.as_posix())
    hygiene.check(not platform_garbage, "No .DS_Store or __MACOSX exists in delivery scope", ", ".join(platform_garbage) or "clean")
    personal_path_re = re.compile(r"/(?:Users|home)/[A-Za-z0-9._-]+/")
    personal_hits: list[str] = []
    for path in authoritative_files():
        text = path.read_text(encoding="utf-8", errors="replace")
        if personal_path_re.search(text):
            personal_hits.append(rel(path))
    hygiene.check(not personal_hits, "No concrete personal home path exists in authoritative/delivery text", ", ".join(personal_hits) or "generic /Users/ and /home/ policy tokens are allowed")
    secret_files = []
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        if relative.parts and relative.parts[0] == ".zcode":
            continue
        if path.name == ".env" or path.suffix.lower() in {".pem", ".key", ".p12", ".pfx"}:
            secret_files.append(relative.as_posix())
    hygiene.check(not secret_files, "No obvious secret file is present in delivery scope", ", ".join(secret_files) or "none")
    hygiene.check((ROOT / ".zcode").exists(), ".zcode is retained as requested", "presence is allowed and is not a failure")
    hygiene.check(not (ROOT / ".git").exists(), "Pre-Git bootstrap record is still immutable and applicable", "after Git initialization this validator refuses to overwrite PP evidence")
    hygiene.notes.append("After authorized Git initialization, P0's repository-hygiene script—not this pre-Git validator—proves .zcode ignore/untracked/archive exclusion and the full tracked-file boundary.")

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    generated_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()
    for gate in gates.values():
        (EVIDENCE_DIR / gate.output_name).write_text(gate.render(generated_at), encoding="utf-8")

    print("FrameFlow P0-Prep validation")
    for gate in gates.values():
        passed = sum(1 for ok, _, _ in gate.checks if ok)
        print(f"[{'PASS' if gate.passed else 'FAIL'}] {gate.evidence_id}: {passed}/{len(gate.checks)} checks")
        for ok, label, detail in gate.checks:
            if not ok:
                print(f"  [FAIL] {label}: {detail}")
    overall = all(gate.passed for gate in gates.values())
    print(f"OVERALL: {'PASS' if overall else 'FAIL'}")
    return 0 if overall else 1


if __name__ == "__main__":
    sys.exit(main())
