#!/usr/bin/env python3
"""tsp-python adapter — routes fixture IDs to tsp-python handlers."""
from pathlib import Path
import sys
import site
import json

# Bootstrap tsp-python venv into system Python's path.
_repo = Path(__file__).resolve().parent.parent
for _sp in (_repo / ".venv" / "lib").glob("python*/site-packages"):
    site.addsitedir(str(_sp))

from tsp_core.schema_parser import parse as schema_parse  # noqa: E402
from tsp_core.type_checker import check as type_check  # noqa: E402
from tsp_core.ir_generator import generate as ir_generate  # noqa: E402
from tsp_core.models import (  # noqa: E402
    typed_schema_from_dict, ast_node_from_dict, tir_result_to_dict,
)


def handle(fixture_id: str, fixture: dict) -> dict:
    try:
        if fixture_id.startswith("schema-parser"):
            result = schema_parse(fixture["yaml"], fixture.get("id", "unknown"))
            return {"output": result}

        if fixture_id.startswith("type-checker"):
            schema = typed_schema_from_dict(fixture["schema"])
            errors = type_check(schema, fixture["data"])
            return {"output": {"errors": [e.to_dict() for e in errors]}}

        if fixture_id.startswith("ir-generator"):
            ast_nodes = [ast_node_from_dict(n) for n in fixture["ast"]]
            result = ir_generate(
                ast_nodes,
                fixture["data"],
                fixture["schema_id"],
                fixture["template_id"],
            )
            return {"output": tir_result_to_dict(result)}

        return {"output": None, "error": f"Unhandled fixture: {fixture_id}"}
    except Exception as exc:
        return {"output": None, "error": str(exc)}


def main() -> None:
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        req = json.loads(raw)
        result = handle(req["fixture_id"], req["fixture"])
        print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
