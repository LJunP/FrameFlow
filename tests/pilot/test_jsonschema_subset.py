from __future__ import annotations

import unittest

from scripts.pilot.jsonschema_subset import validate_instance
from scripts.pilot.pilotlib import PilotError


SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["kind", "count"],
    "properties": {
        "kind": {"enum": ["A", "B"]},
        "count": {"type": "integer", "minimum": 0},
    },
}


class JsonSchemaSubsetBoundaryTest(unittest.TestCase):
    def test_required_is_executed(self) -> None:
        errors = validate_instance({"kind": "A"}, SCHEMA)
        self.assertTrue(any("missing required property count" in item for item in errors))

    def test_additional_properties_is_executed(self) -> None:
        errors = validate_instance({"kind": "A", "count": 1, "extra": True}, SCHEMA)
        self.assertTrue(any("additional property extra" in item for item in errors))

    def test_enum_is_executed(self) -> None:
        errors = validate_instance({"kind": "C", "count": 1}, SCHEMA)
        self.assertTrue(any("not in enum" in item for item in errors))

    def test_valid_instance_passes(self) -> None:
        self.assertEqual([], validate_instance({"kind": "B", "count": 0}, SCHEMA))

    def test_unknown_schema_keyword_fails_closed(self) -> None:
        with self.assertRaises(PilotError):
            validate_instance("x", {"type": "string", "maxLength": 1})


if __name__ == "__main__":
    unittest.main()
