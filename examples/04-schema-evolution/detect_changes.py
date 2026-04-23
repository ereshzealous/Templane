#!/usr/bin/env python3
"""
Detect breaking changes between two Templane schema files.

Usage:
    python3 detect_changes.py <old-schema.yaml> <new-schema.yaml>

Exits 0 if no breaking changes, 1 if any. Useful as a CI gate.
"""
import sys
from pathlib import Path

from templane_core.schema_parser import parse
from templane_core.models import typed_schema_from_dict
from templane_core.breaking_change import detect


def load_schema(path: Path):
    text = path.read_text()
    result = parse(text, schema_id=path.stem)
    if "error" in result:
        print(f"ERROR parsing {path}: {result['error']}", file=sys.stderr)
        sys.exit(2)
    return typed_schema_from_dict(result["schema"])


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__, file=sys.stderr)
        return 2
    old_path, new_path = Path(sys.argv[1]), Path(sys.argv[2])
    old = load_schema(old_path)
    new = load_schema(new_path)

    changes = detect(old, new)

    if not changes:
        print(f"✓ No breaking changes from {old_path.name} → {new_path.name}")
        return 0

    print(f"✗ {len(changes)} breaking change(s) from {old_path.name} → {new_path.name}:")
    for c in changes:
        print(f"  [{c.category}] {c.field_path}: {c.old} → {c.new}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
