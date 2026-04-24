"""05 — Breaking-change detection: compare two schema versions."""
from pathlib import Path
from templane_core.schema_parser import parse as parse_schema
from templane_core.models import typed_schema_from_dict
from templane_core.breaking_change import detect

here = Path(__file__).parent

def load(name: str):
    result = parse_schema((here / name).read_text(), name)
    return typed_schema_from_dict(result["schema"])

old = load("v1.schema.yaml")
new = load("v2.schema.yaml")

changes = detect(old, new)
if not changes:
    print("✓ no breaking changes")
else:
    print(f"{len(changes)} breaking change(s) detected:\n")
    for c in changes:
        print(f"  [{c.category}] {c.field_path}")
        print(f"      old: {c.old}")
        print(f"      new: {c.new}")
