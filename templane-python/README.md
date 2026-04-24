# templane-python

Python implementation of [Templane](../SPEC.md). Ships a **Jinja2 integration**
(`jinja_templane`), a **schema evolution detector** (`breaking_change`), and a
**content hash** utility (`hash`).

**Conformance:** `py 40/40` ✓ — **75 unit tests passing**.

---

## Installation

```bash
cd templane-python
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
| `templane_core`           | Core: models, schema_parser, type_checker, ir_generator, breaking_change, hash |
| `templane_adapter_html`   | HTML output adapter (with entity escaping)        |
| `templane_adapter_yaml`   | YAML output adapter (no escaping)                 |
| `jinja_templane`          | Jinja2 integration: `TemplaneEnvironment` + `TemplaneTemplate` |

---

## Quick start — Jinja2 integration

### Template file (`greeting.templane`):

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
from jinja_templane.environment import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment("./templates")
template = env.get_template("greeting.templane")

# Valid data
print(template.render(name="Alice", status="active"))
# → Hello Alice!
#     Welcome back.

# Invalid data — raises with all errors collected
try:
    template.render(name="Alice", status="actve")
except TemplaneTemplateError as e:
    for err in e.errors:
        print(f"[{err.code}] {err.message}")
    # [invalid_enum_value] Field 'status' value 'actve' not in enum [active, inactive]
```

### Key invariants

- Schema is parsed at `get_template()` time (fail-fast if YAML is malformed
  or the `---` separator is missing).
- Data is type-checked at `render()` time (since data isn't known at load).
- All errors are collected; `TemplaneTemplateError` contains the full list.

---

## Core API

```python
from templane_core.schema_parser import parse
from templane_core.type_checker import check
from templane_core.ir_generator import generate
from templane_core.models import TypedSchema, typed_schema_from_dict

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
from templane_adapter_html.html_adapter import render as html_render
output = html_render(tir)
```

---

## Schema evolution — breaking change detection

`templane_core.breaking_change.detect(old_schema, new_schema)` reports all
breaking changes between two schemas:

```python
from templane_core.breaking_change import detect
from templane_core.models import (
    TypedSchema, TemplaneField, StringType, EnumType,
)

old = TypedSchema(id="v1", fields={
    "email": TemplaneField("email", StringType(), required=False),
    "status": TemplaneField("status", EnumType(["active", "inactive", "pending"]), required=True),
})

new = TypedSchema(id="v2", fields={
    "email": TemplaneField("email", StringType(), required=True),          # optional → required
    "status": TemplaneField("status", EnumType(["active", "inactive"]), required=True),  # dropped "pending"
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

`templane_core.hash.schema_hash(schema)` returns a deterministic SHA-256 hex
digest of a schema's canonical JSON form. Useful for cache invalidation
and schema version tracking:

```python
from templane_core.hash import schema_hash

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
adapters (10), breaking_change (8), hash (3), jinja_templane (6).

---

## Running conformance

From the repo root:

```bash
node templane-spec/templane-conform/dist/cli.js \
  --adapters "py:python3 templane-python/conform-adapter/run.py"
```

Expected: `py: 40/40`.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
