# tsp-go

Go implementation of [TSP](../SPEC.md). Ships a **schema evolution detector**
(`BreakingChangeDetector`) and a **static-binary conform adapter** (no JVM
or interpreter runtime needed).

**Conformance:** `go 32/32` ✓ — **43 unit tests passing**.

---

## Requirements

- Go 1.22+ (for `any` type alias, iteration features).

---

## Install

```bash
cd tsp-go
go build ./...
go test ./...
```

Or use as a Go module:

```go
import "tsp-go/core"
// (Once published to GitHub, the import path will be
//  github.com/<org>/tsp-go/core.)
```

---

## Packages

| Package                 | Purpose                                              |
|-------------------------|------------------------------------------------------|
| `tsp-go/core`           | Models + SchemaParser + TypeChecker + IRGenerator + BreakingChangeDetector |
| `tsp-go/htmladapter`    | HTML rendering (with entity escaping)                |
| `tsp-go/yamladapter`    | YAML rendering (no escaping)                         |
| `tsp-go/cmd/conform-adapter` | Binary for tsp-conform testing                  |

---

## Quick start

### Parse + type-check

```go
package main

import (
    "fmt"
    "tsp-go/core"
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
    "tsp-go/core"
    "tsp-go/htmladapter"
)

ast := []core.ASTNode{
    {Kind: "text", Content: "Hello "},
    {Kind: "expr", Field: "name"},
    {Kind: "text", Content: "!"},
}

tir := core.Generate(ast, map[string]any{"name": "Alice"}, "greeting", "user")
fmt.Println(htmladapter.Render(tir))
// → <!-- tsp template_id=user schema_id=greeting -->
// → Hello Alice!
```

---

## Schema evolution

```go
import "tsp-go/core"

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
discriminated unions in TSP (`TSPFieldType`, `ASTNode`, `TIRNode`) this
implementation uses **flat structs with a `Kind string` discriminator field**
plus **custom `MarshalJSON`/`UnmarshalJSON` methods** that serialize only the
fields appropriate to each kind:

```go
type TSPFieldType struct {
    Kind     string
    Values   []string            // enum
    ItemType *TSPFieldType       // list
    Fields   map[string]TSPField // object
}

func (t TSPFieldType) MarshalJSON() ([]byte, error) {
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
# ok tsp-go/core
# ok tsp-go/htmladapter
# ok tsp-go/yamladapter
# 43 tests passing
```

Breakdown: core schema_parser (7), type_checker (9), ir_generator (9),
breaking_change (8), htmladapter (7), yamladapter (3).

## Running conformance

From the repo root:

```bash
go build -o tsp-go/bin/conform-adapter ./tsp-go/cmd/conform-adapter/
node tsp-spec/tsp-conform/dist/cli.js \
  --adapters "go:tsp-go/bin/conform-adapter"
```

Expected: `go: 32/32`.

---

## Future work

- `gotmpltsp` — TSP integration for Go's stdlib `text/template` (empty
  package dir exists for now).
- `helmtsp` — TSP for Helm charts (sprig-enhanced `text/template`).

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
