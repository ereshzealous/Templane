# tsp-python

Python implementation of [TSP](../SPEC.md). Ships a **Jinja2 integration**
(`jinja_tsp`), a **schema evolution detector** (`breaking_change`), and a
**content hash** utility (`hash`).

**Conformance:** `py 32/32` ✓ — **59 unit tests passing**.

---

## Installation

```bash
cd tsp-python
uv sync --extra dev
```

Requires Python 3.12+ and `uv`.

Or with pip:

```bash
pip install -e '.[dev]'
```

---

## Packages

This repository ships four installable packages:

| Package              | Purpose                                           |
|----------------------|---------------------------------------------------|
| `tsp_core`           | Core: models, schema_parser, type_checker, ir_generator, breaking_change, hash |
| `tsp_adapter_html`   | HTML output adapter (with entity escaping)        |
| `tsp_adapter_yaml`   | YAML output adapter (no escaping)                 |
| `jinja_tsp`          | Jinja2 integration: `TSPEnvironment` + `TSPTemplate` |

---

## Quick start — Jinja2 integration

### Template file (`greeting.tsp`):

```
name:
  type: string
  required: true
status:
  type: enum
  values: [active, inactive]
  required: true
---
Hello {{ name }}!
{% if status == "active" %}
  Welcome back.
{% endif %}
```

### Python:

```python
from jinja_tsp.environment import TSPEnvironment, TSPTemplateError

env = TSPEnvironment("./templates")
template = env.get_template("greeting.tsp")

# Valid data
print(template.render(name="Alice", status="active"))
# → Hello Alice!
#     Welcome back.

# Invalid data — raises with all errors collected
try:
    template.render(name="Alice", status="actve")
except TSPTemplateError as e:
    for err in e.errors:
        print(f"[{err.code}] {err.message}")
    # [invalid_enum_value] Field 'status' value 'actve' not in enum [active, inactive]
```

### Key invariants

- Schema is parsed at `get_template()` time (fail-fast if YAML is malformed
  or the `---` separator is missing).
- Data is type-checked at `render()` time (since data isn't known at load).
- All errors are collected; `TSPTemplateError` contains the full list.

---

## Core API

```python
from tsp_core.schema_parser import parse
from tsp_core.type_checker import check
from tsp_core.ir_generator import generate
from tsp_core.models import TypedSchema, typed_schema_from_dict

# 1. Parse a schema
result = parse(yaml_source, schema_id="user-profile")
schema = typed_schema_from_dict(result["schema"])

# 2. Type-check user data
errors = check(schema, data={"name": "Alice", "age": 30})
for err in errors:
    print(f"[{err.code}] {err.field}: {err.message}")

# 3. Generate a TIR (for adapter rendering)
tir = generate(ast_nodes, data, schema_id="user", template_id="greeting")

# 4. Render
from tsp_adapter_html.html_adapter import render as html_render
output = html_render(tir)
```

---

## Schema evolution — breaking change detection

`tsp_core.breaking_change.detect(old_schema, new_schema)` reports all
breaking changes between two schemas:

```python
from tsp_core.breaking_change import detect
from tsp_core.models import (
    TypedSchema, TSPField, StringType, EnumType,
)

old = TypedSchema(id="v1", fields={
    "email": TSPField("email", StringType(), required=False),
    "status": TSPField("status", EnumType(["active", "inactive", "pending"]), required=True),
})

new = TypedSchema(id="v2", fields={
    "email": TSPField("email", StringType(), required=True),          # optional → required
    "status": TSPField("status", EnumType(["active", "inactive"]), required=True),  # dropped "pending"
})

for change in detect(old, new):
    print(f"{change.category}  {change.field_path}: {change.old} → {change.new}")
# required_change     email: optional → required
# enum_value_removed  status: pending → <removed>
```

Four categories are reported:

- `removed_field` — field absent in new schema
- `required_change` — optional → required
- `type_change` — field type changed
- `enum_value_removed` — enum value dropped

Recurses into `object` fields with dotted paths (e.g. `address.zip`).

See [SPEC.md §8](../SPEC.md#8-schema-evolution) for the normative definition.

---

## Schema hash

`tsp_core.hash.schema_hash(schema)` returns a deterministic SHA-256 hex
digest of a schema's canonical JSON form. Useful for cache invalidation
and schema version tracking:

```python
from tsp_core.hash import schema_hash

print(schema_hash(my_schema))
# → a3f1e9c8b2d5... (64-char hex)
```

Field insertion order does not affect the hash (`sort_keys=True`).

---

## Running tests

```bash
.venv/bin/pytest
# 59 passed
```

Breakdown: models (7), schema_parser (7), type_checker (9), ir_generator (9),
adapters (10), breaking_change (8), hash (3), jinja_tsp (6).

---

## Running conformance

From the repo root:

```bash
node tsp-spec/tsp-conform/dist/cli.js \
  --adapters "py:python3 tsp-python/conform-adapter/run.py"
```

Expected: `py: 32/32`.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
