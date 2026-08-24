"""Small fail-closed JSON Schema 2020-12 subset used by F11.

The project intentionally has no runtime dependency on ``jsonschema``.  This
module executes every validation keyword currently used by the two committed
F11 Schemas and rejects a Schema if a new unsupported keyword appears.  It is
not a general-purpose or fully conforming JSON Schema implementation.
"""

from __future__ import annotations

from datetime import datetime
import json
import re
from typing import Any

from scripts.pilot.pilotlib import PilotError


SUPPORTED_KEYWORDS = {
    "$schema",
    "$id",
    "$ref",
    "$defs",
    "title",
    "description",
    "type",
    "additionalProperties",
    "required",
    "properties",
    "enum",
    "const",
    "pattern",
    "minLength",
    "minimum",
    "maximum",
    "format",
    "items",
    "minItems",
    "maxItems",
    "uniqueItems",
    "allOf",
    "if",
    "then",
}
ANNOTATION_KEYWORDS = {"$schema", "$id", "title", "description"}


def audit_supported_keywords(schema: dict[str, Any]) -> None:
    def visit(node: Any, path: str) -> None:
        if isinstance(node, bool):
            return
        if not isinstance(node, dict):
            raise PilotError(f"Schema node {path} must be an object or boolean")
        unknown = set(node) - SUPPORTED_KEYWORDS
        if unknown:
            raise PilotError(
                f"unsupported JSON Schema keyword at {path}: {', '.join(sorted(unknown))}"
            )
        for container in ("properties", "$defs"):
            raw = node.get(container, {})
            if not isinstance(raw, dict):
                raise PilotError(f"Schema {path}.{container} must be an object")
            for name, child in raw.items():
                visit(child, f"{path}.{container}.{name}")
        for keyword in ("items", "if", "then"):
            if keyword in node:
                visit(node[keyword], f"{path}.{keyword}")
        for keyword in ("allOf",):
            raw = node.get(keyword, [])
            if not isinstance(raw, list):
                raise PilotError(f"Schema {path}.{keyword} must be an array")
            for index, child in enumerate(raw):
                visit(child, f"{path}.{keyword}[{index}]")
        additional = node.get("additionalProperties")
        if isinstance(additional, dict):
            visit(additional, f"{path}.additionalProperties")

    visit(schema, "#")


def _resolve_pointer(root: dict[str, Any], reference: str) -> Any:
    if not reference.startswith("#/"):
        raise PilotError(f"only local JSON Schema references are supported: {reference}")
    node: Any = root
    for raw in reference[2:].split("/"):
        token = raw.replace("~1", "/").replace("~0", "~")
        if not isinstance(node, dict) or token not in node:
            raise PilotError(f"unresolved JSON Schema reference: {reference}")
        node = node[token]
    return node


def _matches_type(instance: Any, expected: str) -> bool:
    if expected == "null":
        return instance is None
    if expected == "boolean":
        return isinstance(instance, bool)
    if expected == "integer":
        return isinstance(instance, int) and not isinstance(instance, bool)
    if expected == "number":
        return isinstance(instance, (int, float)) and not isinstance(instance, bool)
    if expected == "string":
        return isinstance(instance, str)
    if expected == "array":
        return isinstance(instance, list)
    if expected == "object":
        return isinstance(instance, dict)
    raise PilotError(f"unsupported JSON Schema type: {expected}")


def validate_instance(instance: Any, schema: dict[str, Any]) -> list[str]:
    audit_supported_keywords(schema)

    def validate(value: Any, node: Any, path: str) -> list[str]:
        if node is True:
            return []
        if node is False:
            return [f"{path}: schema is false"]
        if "$ref" in node:
            return validate(value, _resolve_pointer(schema, node["$ref"]), path)
        errors: list[str] = []

        if "allOf" in node:
            for child in node["allOf"]:
                errors.extend(validate(value, child, path))
        if "if" in node and not validate(value, node["if"], path) and "then" in node:
            errors.extend(validate(value, node["then"], path))

        expected = node.get("type")
        if expected is not None:
            types = [expected] if isinstance(expected, str) else expected
            if not isinstance(types, list) or not types or not all(isinstance(item, str) for item in types):
                raise PilotError(f"Schema type at {path} must be a string or string array")
            if not any(_matches_type(value, item) for item in types):
                return [f"{path}: expected type {types}, got {type(value).__name__}"]

        if "const" in node and value != node["const"]:
            errors.append(f"{path}: value does not equal const")
        if "enum" in node and value not in node["enum"]:
            errors.append(f"{path}: value is not in enum")

        if isinstance(value, dict):
            required = node.get("required", [])
            for name in required:
                if name not in value:
                    errors.append(f"{path}: missing required property {name}")
            properties = node.get("properties", {})
            for name, child in properties.items():
                if name in value:
                    errors.extend(validate(value[name], child, f"{path}.{name}"))
            if node.get("additionalProperties") is False:
                for name in value.keys() - properties.keys():
                    errors.append(f"{path}: additional property {name} is not allowed")
            elif isinstance(node.get("additionalProperties"), dict):
                for name in value.keys() - properties.keys():
                    errors.extend(
                        validate(value[name], node["additionalProperties"], f"{path}.{name}")
                    )

        if isinstance(value, list):
            if "minItems" in node and len(value) < node["minItems"]:
                errors.append(f"{path}: has fewer than minItems")
            if "maxItems" in node and len(value) > node["maxItems"]:
                errors.append(f"{path}: has more than maxItems")
            if node.get("uniqueItems") is True:
                canonical = [json.dumps(item, sort_keys=True, separators=(",", ":")) for item in value]
                if len(canonical) != len(set(canonical)):
                    errors.append(f"{path}: array items are not unique")
            if "items" in node:
                for index, item in enumerate(value):
                    errors.extend(validate(item, node["items"], f"{path}[{index}]"))

        if isinstance(value, str):
            if "minLength" in node and len(value) < node["minLength"]:
                errors.append(f"{path}: string is shorter than minLength")
            if "pattern" in node and re.search(node["pattern"], value) is None:
                errors.append(f"{path}: string does not match pattern")
            if node.get("format") == "date-time":
                try:
                    parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
                    if parsed.tzinfo is None:
                        raise ValueError
                except ValueError:
                    errors.append(f"{path}: string is not a timezone-aware date-time")

        if isinstance(value, (int, float)) and not isinstance(value, bool):
            if "minimum" in node and value < node["minimum"]:
                errors.append(f"{path}: number is below minimum")
            if "maximum" in node and value > node["maximum"]:
                errors.append(f"{path}: number is above maximum")
        return errors

    return validate(instance, schema, "$")
