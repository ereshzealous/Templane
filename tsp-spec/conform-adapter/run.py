#!/usr/bin/env python3
"""tsp-spec reference adapter — routes fixture IDs to tsp-core handlers."""
from pathlib import Path
import sys
import site
import json

# Bootstrap tsp-core venv into system Python's path.
# tsp-conform spawns this as "python3 run.py" using the system Python,
# not the venv Python. This uses site.addsitedir() so that .pth files
# (including the editable-install pointer for tsp_core) are processed.
_repo = Path(__file__).resolve().parent.parent
for _sp in (_repo / "tsp-core" / ".venv" / "lib").glob("python*/site-packages"):
    site.addsitedir(str(_sp))

from tsp_core.schema_parser import parse as schema_parse  # noqa: E402


def handle(fixture_id: str, fixture: dict) -> dict:
    try:
        if fixture_id.startswith("schema-parser"):
            result = schema_parse(fixture["yaml"], fixture.get("id", "unknown"))
            return {"output": result}

        # Categories 2-4 added in later tasks
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
