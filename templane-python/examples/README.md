# templane-python — examples

Six runnable Python examples. Each is self-contained: a schema file
plus a `run.py` that executes against it.

## Setup

```bash
cd templane-python
uv sync --extra dev
```

## Run everything

```bash
for d in examples/*/; do
  echo "=== $d ==="
  .venv/bin/python "$d/run.py"
done
```

## The six examples

| # | Path | What it shows |
|---|------|---------------|
| 01 | [`01-hello/`](01-hello/) | Load a `.templane` file, validate, render |
| 02 | [`02-validation-errors/`](02-validation-errors/) | All 4 error codes surfacing in one pass |
| 03 | [`03-nested-and-lists/`](03-nested-and-lists/) | Nested objects, enums, lists, Jinja filters |
| 04 | [`04-jinja-binding/`](04-jinja-binding/) | Real Jinja features (if/for, filters) on validated data |
| 05 | [`05-breaking-change/`](05-breaking-change/) | Detect schema v1 → v2 breaking changes |
| 06 | [`06-sidecar/`](06-sidecar/) | **Sidecar mode**: keep your `.jinja` files, add a schema beside them |

## 01 — Hello

Minimal surface area. `TemplaneEnvironment` works like a Jinja
`Environment` but every `.render()` call type-checks first.

```python
from jinja_templane import TemplaneEnvironment
env = TemplaneEnvironment(here)
tmpl = env.get_template("greeting.templane")
print(tmpl.render(name="Arya", temperature_c=22, is_morning=True))
```

**Output:**
```
Good morning, Arya!
It's 22°C outside today.
```

## 02 — Validation errors

Feed the renderer bad data and watch every error surface with its code
and field path. All errors are collected — not just the first.

Expected output:
```
render refused: 4 error(s)

  [missing_required_field] name: Required field 'name' is missing
  [type_mismatch] age: Field 'age' expected number, got string
  [invalid_enum_value] role: Field 'role' value 'superuser' not in enum [admin, editor, viewer]
  [did_you_mean] rol: Unknown field 'rol'. Did you mean 'role'?
```

## 03 — Nested objects and lists

A realistic order receipt with a nested `customer`, a list of `items`
with their own sub-schema, and Jinja's `|format` filter for money.

## 04 — Jinja binding

Shows that Jinja's full language is available after the type check
passes — conditionals, loops, filters. The check only refuses bad
*data*; it doesn't restrict what you can express in the template.

## 05 — Breaking-change detection

Parse two schema versions, diff them, print the breaking changes with
the category (`removed_field`, `required_change`, `type_change`,
`enum_value_removed`).

## 06 — External-body schema

The adoption pattern (SPEC 1.1 §4.3). `password_reset.jinja` is a plain
Jinja2 template — no Templane-specific syntax, editable in any Jinja
tool. `password_reset.schema.yaml` sits beside it and references the
body via `body: ./password_reset.jinja`.

`TemplaneEnvironment.get_template()` handles sidecar transparently — no
new API. Run the example to see it render cleanly, then refuse bad data
with `missing_required_field` + `type_mismatch` before Jinja ever runs.

This is how you adopt Templane on an existing Jinja codebase: keep your
`.jinja` files, drop a schema next to each one.
