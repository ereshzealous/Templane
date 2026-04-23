# 01 — Basic Hello ⭐

The smallest possible Templane example: one required string field, one
template, one call to `render`.

## Files

| File                  | What it is |
|-----------------------|------------|
| `greeting.templane`   | Schema (1 field) + template body, separated by `\n---\n` |
| `data.json`           | Valid input |
| `data-invalid.json`   | Invalid input — misspelled `name` as `naem` to trigger a did-you-mean error |

## The template

```
name:
  type: string
  required: true
---
Hello, {{ name }}!
```

Two sections: **schema** above `---`, **template body** below. The template
syntax is the target engine's (Handlebars, Jinja2, FreeMarker, etc.).

## Run — TypeScript + Handlebars

The `xt` CLI ships with `templane-ts`:

```bash
# From the repo root:
node templane-ts/dist/xt.js render examples/01-basic-hello/greeting.templane \
  examples/01-basic-hello/data.json
# Output:
# Hello, World!
```

Now try the invalid input:

```bash
node templane-ts/dist/xt.js render examples/01-basic-hello/greeting.templane \
  examples/01-basic-hello/data-invalid.json
# exits non-zero; writes to stderr:
#   Data does not match schema:
#     [missing_required_field] Required field 'name' is missing
#     [did_you_mean]           Unknown field 'naem'. Did you mean 'name'?
```

That's the whole value proposition in four lines of output.

## Run — Python + Jinja2

```python
from jinja_templane.environment import TSPEnvironment, TSPTemplateError

env = TSPEnvironment("examples/01-basic-hello")
tmpl = env.get_template("greeting.templane")

# Valid
print(tmpl.render(name="World"))
# → Hello, World!

# Invalid
try:
    tmpl.render(naem="World")
except TSPTemplateError as e:
    for err in e.errors:
        print(f"[{err.code}] {err.message}")
```

## What to take away

- One YAML block declares the data shape.
- The template body uses whatever engine's syntax.
- Valid data renders. Invalid data fails loudly, with **every error at once**.
- No tests to write. The schema IS the contract.

→ Next: [`02-user-profile`](../02-user-profile/) shows nested objects, enums, and lists.
