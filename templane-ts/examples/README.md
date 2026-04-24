# templane-ts — examples

Four runnable TypeScript examples showing the `handlebars-templane`
binding. Each is self-contained: a `.templane` file plus `run.ts`.

## Setup

```bash
cd templane-ts
npm install
npm run build
```

## Run everything

```bash
for d in examples/*/; do
  echo "=== $d ==="
  ./node_modules/.bin/tsx "$d/run.ts"
done
```

(`tsx` is already a devDep — no global install needed.)

## The four examples

| # | Path | What it shows |
|---|------|---------------|
| 01 | [`01-hello/`](01-hello/) | Compile, render, print |
| 02 | [`02-validation-errors/`](02-validation-errors/) | All 4 error codes + `did_you_mean` |
| 03 | [`03-nested-and-lists/`](03-nested-and-lists/) | Nested object, enum, list-of-objects |
| 04 | [`04-handlebars-binding/`](04-handlebars-binding/) | Handlebars helpers (`eq`) over validated data |

## 01 — Hello

```ts
import { compile } from 'handlebars-templane';
const tmpl = compile(readFileSync('greeting.templane', 'utf8'));
console.log(tmpl.render({ name: 'Arya', temperature_c: 22, is_morning: true }));
```

**Output:**
```
Good morning, Arya!
It's 22°C outside today.
```

## 02 — Validation errors

`tmpl.render(bad)` throws a `TemplaneHandlebarsError` whose `.errors`
property lists every violation. All errors are collected — not just
the first.

Expected output:
```
render refused: 4 error(s)

  [missing_required_field] name: Required field 'name' is missing
  [type_mismatch] age: Field 'age' expected number, got string
  [invalid_enum_value] role: Field 'role' value 'superuser' not in enum [admin, editor, viewer]
  [did_you_mean] rol: Unknown field 'rol'. Did you mean 'role'?
```

## 03 — Nested objects and lists

An order receipt with a nested `customer`, a list of `items` with their
own sub-schema, and Handlebars' native `{{#each}}` over the list.

## 04 — Handlebars binding

Registers a custom Handlebars helper (`eq` for enum dispatch), then
renders a notification email. Shows that Handlebars' full helper system
is available — the type check only governs what data shape the
template will accept.

## What's not included

Breaking-change detection is currently available in **templane-python**,
**templane-java**, and **templane-go**. A TS port is planned.
