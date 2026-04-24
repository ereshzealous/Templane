# templane-go

Go implementation of [Templane](../SPEC.md). Ships a **schema evolution detector**
(`BreakingChangeDetector`) and a **static-binary conform adapter** (no JVM
or interpreter runtime needed).

**Conformance:** `go 40/40` ✓ — **56 unit tests passing**.

---

## Requirements

- Go 1.22+ (for `any` type alias, iteration features).

---

## Install

```bash
cd templane-go
go build ./...
go test ./...
```

Or use as a Go module:

```bash
go get github.com/ereshzealous/Templane/templane-go@latest
```

```go
import "github.com/ereshzealous/Templane/templane-go/core"
```

---

## Packages

| Package                 | Purpose                                              |
|-------------------------|------------------------------------------------------|
| `github.com/ereshzealous/Templane/templane-go/core`           | Models + SchemaParser + TypeChecker + IRGenerator + BreakingChangeDetector |
| `github.com/ereshzealous/Templane/templane-go/htmladapter`    | HTML rendering (with entity escaping)                |
| `github.com/ereshzealous/Templane/templane-go/yamladapter`    | YAML rendering (no escaping)                         |
| `github.com/ereshzealous/Templane/templane-go/cmd/conform-adapter` | Binary for templane-conform testing                  |

---

## Quick start

### Parse + type-check

```go
package main

import (
    "fmt"
    "github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
    yaml := `
name:
  type: string
  required: true
age:
  type: number
`
    result := core.ParseSchema(yaml, "user-profile")
    if result.Error != "" {
        panic(result.Error)
    }

    errors := core.Check(result.Schema, map[string]any{
        "naem": "Alice",  // typo
        "age":  "thirty", // wrong type
    })

    for _, e := range errors {
        fmt.Printf("[%s] %s\n", e.Code, e.Message)
    }
    // [missing_required_field] Required field 'name' is missing
    // [did_you_mean]           Unknown field 'naem'. Did you mean 'name'?
    // [type_mismatch]          Field 'age' expected number, got string
}
```

### Generate TIR and render

```go
import (
    "github.com/ereshzealous/Templane/templane-go/core"
    "github.com/ereshzealous/Templane/templane-go/htmladapter"
)

ast := []core.ASTNode{
    {Kind: "text", Content: "Hello "},
    {Kind: "expr", Field: "name"},
    {Kind: "text", Content: "!"},
}

tir := core.Generate(ast, map[string]any{"name": "Alice"}, "greeting", "user")
fmt.Println(htmladapter.Render(tir))
// → <!-- templane template_id=user schema_id=greeting -->
// → Hello Alice!
```

---

## Schema evolution

```go
import "github.com/ereshzealous/Templane/templane-go/core"

changes := core.DetectBreakingChanges(oldSchema, newSchema)
for _, c := range changes {
    fmt.Printf("%s  %s: %s → %s\n", c.Category, c.FieldPath, c.Old, c.New)
}
```

Four categories: `removed_field`, `required_change`, `type_change`,
`enum_value_removed`. Recurses into `object` fields. See
[SPEC.md §8](../SPEC.md#8-schema-evolution).

---

## Design notes

### Flat structs + custom JSON

Go has no sum types or pattern matching on algebraic types. For the three
discriminated unions in Templane (`TemplaneFieldType`, `ASTNode`, `TIRNode`) this
implementation uses **flat structs with a `Kind string` discriminator field**
plus **custom `MarshalJSON`/`UnmarshalJSON` methods** that serialize only the
fields appropriate to each kind:

```go
type TemplaneFieldType struct {
    Kind     string
    Values   []string            // enum
    ItemType *TemplaneFieldType       // list
    Fields   map[string]TemplaneField // object
}

func (t TemplaneFieldType) MarshalJSON() ([]byte, error) {
    m := map[string]any{"kind": t.Kind}
    switch t.Kind {
    case "enum":   m["values"] = t.Values
    case "list":   m["item_type"] = t.ItemType
    case "object": m["fields"] = t.Fields
    }
    return json.Marshal(m)
}
```

This avoids `omitempty` traps on TIR's `resolved: null` (which must survive
serialization even when `nil`) — a bug that would otherwise be silent and
break conformance.

### Static binary distribution

The conform adapter builds to a single native executable:

```bash
go build -o bin/conform-adapter ./cmd/conform-adapter/
```

No JVM, no Python runtime — ~3 MB self-contained binary, ideal for CI/CD
environments and container images.

### Dependencies

- `gopkg.in/yaml.v3` — YAML parsing (de-facto Go standard)
- stdlib only for JSON, HTML escape, subprocess I/O, testing

---

## Running tests

```bash
go test ./...
# ok templane-go/core
# ok templane-go/htmladapter
# ok templane-go/yamladapter
# 56 tests passing
```

Breakdown: core schema_parser (7), type_checker (9), ir_generator (9),
breaking_change (8), htmladapter (7), yamladapter (3).

## Running conformance

From the repo root:

```bash
go build -o templane-go/bin/conform-adapter ./templane-go/cmd/conform-adapter/
node templane-spec/templane-conform/dist/cli.js \
  --adapters "go:templane-go/bin/conform-adapter"
```

Expected: `go: 40/40`.

---

## Future work

- `gotmpltemplane` — Templane integration for Go's stdlib `text/template` (empty
  package dir exists for now).
- `helmtemplane` — Templane for Helm charts (sprig-enhanced `text/template`).

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
