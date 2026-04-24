# templane-go — examples

Four runnable Go examples. Each is its own Go main package with a
`.templane` (or `.yaml`) file next to it.

## Prereqs

Go 1.22+. Run from the repo root once to hydrate the module:

```bash
cd templane-go
go build ./...
```

## Run everything

```bash
for d in examples/*/; do
  echo "=== $d ==="
  (cd "$d" && go run main.go)
done
```

## The four examples

| # | Path | What it shows |
|---|------|---------------|
| 01 | [`01-hello/`](01-hello/) | Parse schema, check data, render with `text/template` |
| 02 | [`02-validation-errors/`](02-validation-errors/) | All 4 error codes in one pass |
| 03 | [`03-nested-and-lists/`](03-nested-and-lists/) | Nested object, enum, list-of-objects |
| 04 | [`04-breaking-change/`](04-breaking-change/) | Diff two schema versions |

## Rendering approach in Go

`templane-go` ships the schema parser + type checker + breaking-change
detector. For *rendering*, these examples call Go's standard-library
`text/template` against the validated data. That's the practical
pattern today — Templane guards the inputs, `text/template` does the
substitution.

(A future `gotmpltemplane` package will wrap this pattern the way
`jinja_templane` and `freemarker-templane` do for Python and Java.)

## 01 — Hello

```go
parsed := core.ParseSchema(source, "greeting.templane")
errs := core.Check(parsed.Schema, data)
if len(errs) > 0 { /* refuse */ }
tmpl := template.Must(template.New("t").Parse(*parsed.Body))
tmpl.Execute(os.Stdout, data)
```

**Output:**
```
Good morning, Arya!
It's 22°C outside today.
```

## 02 — Validation errors

Trips every error the checker produces: `missing_required_field`,
`type_mismatch`, `invalid_enum_value`, and `did_you_mean` (the
Levenshtein-distance suggestion for mistyped field names). All errors
come back in one pass.

```
check refused: 4 error(s)

  [missing_required_field] name: Required field 'name' is missing
  [type_mismatch] age: Field 'age' expected number, got string
  [invalid_enum_value] role: Field 'role' value 'superuser' not in enum [admin, editor, viewer]
  [did_you_mean] rol: Unknown field 'rol'. Did you mean 'role'?
```

## 03 — Nested objects and lists

A realistic order receipt: nested `customer` (object with enum `tier`),
a list of `items`, and a money formatter via `template.FuncMap`.

## 04 — Breaking-change detection

Parse v1 and v2 schema files, compare them, print the breaking changes.
Categories: `removed_field`, `required_change`, `type_change`,
`enum_value_removed`. Safe changes (new optional fields, enum additions)
are not reported.
