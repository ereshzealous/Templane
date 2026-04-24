# templane-python

**Python implementation of [Templane](https://github.com/ereshzealous/Templane)** — typed template contracts for Jinja2.

Templane adds compile-time schema validation to your templates. Define what data your template expects in a small `.schema.yaml` file next to your `.jinja`, and Templane catches missing fields, typos, and wrong types **before Jinja renders**, not at 2am in production.

- **Conformance:** 40/40 fixtures across the [Templane protocol](https://github.com/ereshzealous/Templane/blob/main/SPEC.md) · 75 unit tests
- **Engine binding:** Jinja2 (`jinja_templane`)
- **Also ships:** breaking-change detector, schema hash
- **Runtime:** Python 3.12+
- **License:** Apache 2.0

---

## Install

```bash
pip install templane-python
# or with uv:
uv add templane-python
```

---

## Quick start

Create `email.jinja` (plain Jinja2 — not modified by Templane):

```jinja
Hi {{ user.name }}! Your order #{{ order_id }} total is ${{ amount }}.
```

Create `email.schema.yaml` next to it (declares the data contract):

```yaml
body: ./email.jinja
engine: jinja

user:
  type: object
  required: true
  fields:
    name: { type: string, required: true }
order_id:
  type: string
  required: true
amount:
  type: number
  required: true
```

Use from Python:

```python
from jinja_templane import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment("templates")
tmpl = env.get_template("email.schema.yaml")

try:
    output = tmpl.render(
        user={"name": "Alice"},
        order_id="INV-042",
        amount=99.00,
    )
    print(output)
    # → "Hi Alice! Your order #INV-042 total is $99.0."
except TemplaneTemplateError as exc:
    for err in exc.errors:
        print(f"[{err.code}] {err.field}: {err.message}")
```

---

## Validation errors — caught before rendering

```python
# Missing field + wrong type both trip at once
try:
    tmpl.render(
        # user missing entirely
        order_id=42,      # wrong type
        amount="free",    # wrong type
    )
except TemplaneTemplateError as exc:
    for err in exc.errors:
        print(f"[{err.code}] {err.field}")

# [missing_required_field] user
# [type_mismatch] order_id
# [type_mismatch] amount
```

All errors are collected — never short-circuits at the first.

---

## Breaking-change detection

Detect schema evolution issues before they break downstream data:

```python
from templane_core.schema_parser import parse as parse_schema
from templane_core.models import typed_schema_from_dict
from templane_core.breaking_change import detect

def load(yaml_str: str, name: str):
    result = parse_schema(yaml_str, name)
    return typed_schema_from_dict(result["schema"])

old = load(open("schema-v1.yaml").read(), "v1")
new = load(open("schema-v2.yaml").read(), "v2")

changes = detect(old, new)
for c in changes:
    print(f"[{c.category}] {c.field_path}: {c.old} → {c.new}")

# Four categories:
#   removed_field        — schema had the field; now doesn't
#   required_change      — optional → required
#   type_change          — field type changed
#   enum_value_removed   — an enum value was removed
```

Safe changes (new optional field, added enum value, required → optional) are NOT reported.

---

## API

### `jinja_templane` — the engine binding

```python
from jinja_templane import (
    TemplaneEnvironment,
    TemplaneTemplate,
    TemplaneTemplateError,
)

# Like jinja2.Environment, but get_template returns a TemplaneTemplate
env = TemplaneEnvironment(search_path: str | Path)
env.get_template(name: str) -> TemplaneTemplate
    # Handles both .schema.yaml (with body: reference) and legacy inline-body .templane files

tmpl.render(**data) -> str  # raises TemplaneTemplateError on validation failure
tmpl.schema                  # the parsed TypedSchema
```

### `templane_core` — the protocol primitives

```python
from templane_core.schema_parser import parse, load_from_path
from templane_core.type_checker import check
from templane_core.ir_generator import generate
from templane_core.breaking_change import detect
from templane_core.hash import schema_hash

# parse(yaml_str, schema_id) → dict with {schema, body?, body_path?, engine?, error?}
# load_from_path(path) → same shape, with body resolved from disk when sidecar
# check(schema, data) → list[TypeCheckError]
# generate(ast, data, schema_id, template_id) → TIRResult
# detect(old_schema, new_schema) → list[BreakingChange]
# schema_hash(schema) → "sha256:..." stable across equivalent schemas
```

---

## Why Templane

Templates are untyped contracts. They accept a bag of values, look up names by string, and render *something* — even when the data has a typo, a missing field, or a wrong type. The failure is silent: the render succeeds, the customer gets a broken email, and you find out four days later.

Templane fixes this at the boundary. A schema next to your template declares what the template expects; the binding refuses to render when the data doesn't match. See the [main README](https://github.com/ereshzealous/Templane) for the full pitch.

---

## Adoption pattern

**You don't migrate templates.** Your existing `.jinja` files stay as-is. You drop one `.schema.yaml` beside each one:

```
templates/
  welcome.jinja                 ← untouched
  welcome.schema.yaml           ← NEW
  invoice.jinja                 ← untouched
  invoice.schema.yaml           ← NEW
```

Your code switches from `jinja2.Environment` to `jinja_templane.TemplaneEnvironment`. That's the migration.

See the [ADOPTION guide](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md) for per-engine walkthroughs.

---

## Examples

Six worked examples under the repo's [`templane-python/examples/`](https://github.com/ereshzealous/Templane/tree/main/templane-python/examples): hello, validation errors, nested objects and lists, Jinja features (filters, `if`/`for`), breaking-change detection, and a full password-reset email demo.

---

## Building from source

```bash
git clone https://github.com/ereshzealous/Templane.git
cd Templane/templane-python
uv sync --extra dev
.venv/bin/pytest   # 75 tests
```

---

## Links

- **Repo**: https://github.com/ereshzealous/Templane
- **Full spec (RFC 2119)**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- **Architecture**: [docs/ARCHITECTURE.md](https://github.com/ereshzealous/Templane/blob/main/docs/ARCHITECTURE.md) — 12 Mermaid diagrams
- **Adoption guide**: [docs/ADOPTION.md](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md)
- **Issues**: [GitHub Issues](https://github.com/ereshzealous/Templane/issues)

## License

Apache License 2.0
