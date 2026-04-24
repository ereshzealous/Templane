# templane-go

**Go implementation of [Templane](https://github.com/ereshzealous/Templane)** — typed template contracts paired with Go's stdlib `text/template`.

Templane adds compile-time schema validation to your templates. Define what data your template expects in a small `.schema.yaml` file next to your `.tmpl`, and Templane catches missing fields, typos, and wrong types **before `text/template` renders**, not at 2am in production.

- **Conformance:** 40/40 fixtures across the [Templane protocol](https://github.com/ereshzealous/Templane/blob/main/SPEC.md) · 56 unit tests
- **Template engine:** Go standard library `text/template` / `html/template`
- **Also ships:** breaking-change detector, static-binary conform adapter (no JVM, no interpreter)
- **Runtime:** Go 1.22+
- **License:** Apache 2.0

---

## Install

```bash
go get github.com/ereshzealous/Templane/templane-go@latest
```

Import:

```go
import "github.com/ereshzealous/Templane/templane-go/core"
```

---

## Quick start

Create `templates/service.tmpl` (plain Go text/template — not modified by Templane):

```
[Unit]
Description={{.description}}

[Service]
ExecStart={{.exec_start}}
Restart={{.restart_policy}}
```

Create `templates/service.schema.yaml` next to it:

```yaml
body: ./service.tmpl
engine: gotemplate

description:
  type: string
  required: true
exec_start:
  type: string
  required: true
restart_policy:
  type: enum
  values: [no, on-failure, always]
  required: true
```

Use from Go:

```go
package main

import (
    "fmt"
    "os"
    "text/template"

    "github.com/ereshzealous/Templane/templane-go/core"
)

func main() {
    // 1. Load schema (+ body) from disk
    r := core.LoadSchemaFromPath("templates/service.schema.yaml")
    if r.Error != "" {
        fmt.Fprintln(os.Stderr, r.Error)
        os.Exit(1)
    }

    data := map[string]any{
        "description":    "My service",
        "exec_start":     "/usr/local/bin/run",
        "restart_policy": "on-failure",
    }

    // 2. Type-check data against schema
    if errs := core.Check(r.Schema, data); len(errs) > 0 {
        for _, e := range errs {
            fmt.Fprintf(os.Stderr, "[%s] %s: %s\n", e.Code, e.Field, e.Message)
        }
        os.Exit(1)
    }

    // 3. Render with stdlib text/template
    tmpl := template.Must(template.New("svc").Parse(*r.Body))
    tmpl.Execute(os.Stdout, data)
}
```

Three lines of Templane; rendering stays stdlib.

---

## Validation errors — caught before rendering

```go
badData := map[string]any{
    // "description" missing
    "exec_start":     "/usr/local/bin/run",
    "restart_policy": "sometimes",  // invalid enum
}

if errs := core.Check(r.Schema, badData); len(errs) > 0 {
    for _, e := range errs {
        fmt.Printf("[%s] %s: %s\n", e.Code, e.Field, e.Message)
    }
}
// [missing_required_field] description: Required field 'description' is missing
// [invalid_enum_value] restart_policy: Field 'restart_policy' value 'sometimes' not in enum [no, on-failure, always]
```

All errors are collected — never short-circuits at the first.

---

## Breaking-change detection

Detect schema evolution issues before they break downstream data:

```go
import "github.com/ereshzealous/Templane/templane-go/core"

oldR := core.ParseSchema(oldYAML, "v1")
newR := core.ParseSchema(newYAML, "v2")

changes := core.DetectBreakingChanges(oldR.Schema, newR.Schema)
for _, c := range changes {
    fmt.Printf("[%s] %s: %s → %s\n", c.Category, c.FieldPath, c.Old, c.New)
}
```

Four categories: `removed_field`, `required_change`, `type_change`, `enum_value_removed`. Safe additions are NOT reported.

---

## API

### `github.com/.../templane-go/core`

```go
// Load a schema (embedded or external-body) from disk
func LoadSchemaFromPath(schemaPath string) ParseResult

// Parse a schema YAML string
func ParseSchema(yamlStr, schemaID string) ParseResult

// Validate data against a schema
func Check(schema *TypedSchema, data map[string]any) []TypeCheckError

// Build a typed IR from AST + data
func Generate(ast []ASTNode, data map[string]any, schemaID, templateID string) TIRResult

// Diff two schemas for breaking changes
func DetectBreakingChanges(oldSchema, newSchema *TypedSchema) []BreakingChange

type ParseResult struct {
    Schema   *TypedSchema `json:"schema,omitempty"`
    Body     *string      `json:"body,omitempty"`
    BodyPath *string      `json:"body_path,omitempty"`
    Engine   *string      `json:"engine,omitempty"`
    Error    string       `json:"error,omitempty"`
}
```

### Output adapters (optional)

```go
import (
    "github.com/ereshzealous/Templane/templane-go/htmladapter"
    "github.com/ereshzealous/Templane/templane-go/yamladapter"
)

htmladapter.Render(tir)  // HTML output with entity escaping + provenance markers
yamladapter.Render(tir)  // YAML output
```

For most Go users, stdlib `text/template` after a `core.Check` is all you need.

---

## Why Templane

Templates are untyped contracts. They accept a bag of values, look up names by string, and render *something* — even when the data has a typo, a missing field, or a wrong type. The failure is silent: the render succeeds, the customer gets a broken email, and you find out four days later.

Templane fixes this at the boundary. A schema next to your template declares what the template expects; the checker refuses bad data before you hand it to `text/template`. See the [main README](https://github.com/ereshzealous/Templane) for the full pitch.

---

## Adoption pattern

**You don't migrate templates.** Your existing `.tmpl` files stay as-is. You drop one `.schema.yaml` beside each one:

```
templates/
  deployment.tmpl                 ← untouched
  deployment.schema.yaml          ← NEW
  configmap.tmpl                  ← untouched
  configmap.schema.yaml           ← NEW
```

Your code adds three lines: `LoadSchemaFromPath` + `Check` + handle errors. That's the migration.

See the [ADOPTION guide](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md) for per-engine walkthroughs.

---

## Examples

Five worked examples under the repo's [`templane-go/examples/`](https://github.com/ereshzealous/Templane/tree/main/templane-go/examples): hello, validation errors, nested objects and lists, breaking-change detection, and a full sidecar-mode systemd unit generator.

---

## Building from source

```bash
git clone https://github.com/ereshzealous/Templane.git
cd Templane/templane-go
go build ./...
go test ./...   # 56 tests
```

---

## Static binary — the Go advantage

The conform-adapter compiles to a single self-contained binary (~3 MB, no runtime deps):

```bash
go build -o bin/conform-adapter ./cmd/conform-adapter/
GOOS=linux GOARCH=amd64 go build -o bin/conform-adapter-linux ./cmd/conform-adapter/   # cross-compile
```

Ideal for CI containers and minimal sidecars where JVM/Node startup cost matters.

---

## Links

- **Repo**: https://github.com/ereshzealous/Templane
- **Full spec (RFC 2119)**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- **Architecture**: [docs/ARCHITECTURE.md](https://github.com/ereshzealous/Templane/blob/main/docs/ARCHITECTURE.md) — 12 Mermaid diagrams
- **Adoption guide**: [docs/ADOPTION.md](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md)
- **Issues**: [GitHub Issues](https://github.com/ereshzealous/Templane/issues)

## License

Apache License 2.0
