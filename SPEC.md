# Templane — Templane Protocol

**Specification version:** 1.1
**Status:** Stable
**Last updated:** 2026-04-24

**Changes in 1.1:** Adds §4.3 Sidecar mode — schemas MAY reference an
external body file via the reserved top-level `body:` key instead of
inlining the body after `---`. Adds the reserved `engine:` key for
declaring the template engine explicitly. Backward-compatible: every
1.0 schema is a valid 1.1 schema.

## Abstract

Templane (Templane Protocol) is a language-neutral protocol that adds
compile-time type safety to templating engines. It defines:

1. A YAML-based **schema document format** describing the shape of template
   data (fields, types, optionality).
2. A **typed intermediate representation (TIR)** that any output adapter can
   render.
3. A **type checker** that validates data against schemas and produces
   structured errors.
4. A **conformance fixture suite** (the `templane-conform` CLI + 32 fixtures) any
   compliant implementation must pass.

Any templating engine that adopts Templane gains compile-time type checking,
schema validation, schema evolution tracking, and IDE tooling —
without replacing the engine.

---

## Table of contents

1. [Introduction](#1-introduction)
2. [Notation and conventions](#2-notation-and-conventions)
3. [Type system](#3-type-system)
4. [Schema document format](#4-schema-document-format)
5. [Wire format (JSON)](#5-wire-format-json)
6. [Operations](#6-operations)
7. [Type-checking semantics](#7-type-checking-semantics)
8. [Schema evolution](#8-schema-evolution)
9. [Conform adapter protocol](#9-conform-adapter-protocol)
10. [Conformance](#10-conformance)
11. [References](#11-references)
12. Appendix A — [Example schemas](#appendix-a--example-schemas)
13. Appendix B — [Fixture index](#appendix-b--fixture-index)

---

## 1. Introduction

### 1.1 Problem

Every mainstream templating engine (Jinja2, FreeMarker, Handlebars, Mustache,
Liquid, Go templates, etc.) shares the same fundamental flaw: **templates are
untyped contracts with zero compile-time enforcement.** The following failures
are silent at render time:

1. Misspelled field name — `{{ user.naem }}` renders blank.
2. Wrong type passed — number where a list is expected.
3. Missing required field — blank output, no error.
4. Optional field treated as required — crashes when absent.
5. Enum value typo — `status: "actve"` passes through.
6. Nested path error — `{{ order.customer.addr.city }}` fails at depth.
7. Breaking schema change — a backend renames a field, templates break.
8. No discoverability — no IDE hover docs, no autocomplete.

### 1.2 Approach

Templane decouples the schema layer from the templating engine, analogous to how
LSP decoupled language intelligence from editors. Any engine adopting Templane
inherits compile-time type checking, structured error reporting, and
schema-evolution detection — regardless of the engine's own template syntax.

### 1.3 Scope

This specification defines:

- The schema document format (YAML, §4).
- The JSON wire format for schemas, ASTs, TIRs, and errors (§5).
- The four core operations every implementation MUST provide (§6).
- Type-checking semantics including path notation and error codes (§7).
- The schema-evolution detector's breaking-change categories (§8).
- The conform adapter protocol used for cross-implementation testing (§9).

This specification does NOT define:

- Template syntax — each engine (Jinja2, Handlebars, etc.) retains its
  native syntax. Templane operates on data going into those engines.
- How engines parse their own templates into an AST — the AST format is
  defined (§5.4) but the parsing strategy is engine-specific.
- Language bindings — each implementation is free to use idiomatic types
  (dataclasses, sealed interfaces, discriminated unions, structs).

---

## 2. Notation and conventions

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**,
**SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY**, and **OPTIONAL** in this
document are to be interpreted as described in RFC 2119.

JSON examples use two-space indentation. YAML examples follow canonical YAML
1.2 style. Code examples in the normative text are illustrative; the
normative definitions are the type signatures and the fixture suite.

---

## 3. Type system

### 3.1 TemplaneFieldType

Every field in a Templane schema has exactly one of seven types, distinguished by
the `kind` tag:

| Kind       | Meaning                                | Additional keys               |
|------------|----------------------------------------|-------------------------------|
| `string`   | UTF-8 text                             | —                             |
| `number`   | integer or floating-point              | —                             |
| `boolean`  | `true` or `false`                      | —                             |
| `null`     | the JSON null value                    | —                             |
| `enum`     | one of an explicit string set          | `values: string[]`            |
| `list`     | homogeneous array                      | `item_type: TemplaneFieldType`     |
| `object`   | nested mapping                         | `fields: map<string, TemplaneField>` |

Types are recursive: `list<object<...>>` and arbitrarily deep `object` nesting
are valid.

### 3.2 TemplaneField

A field MUST have three properties:

- `name: string` — the field identifier.
- `type: TemplaneFieldType` — the field's declared type.
- `required: boolean` — whether omission is an error.

### 3.3 TypedSchema

A schema MUST have two properties:

- `id: string` — an identifier (used in error paths and provenance).
- `fields: map<string, TemplaneField>` — keyed by field name.

Field order in the map SHOULD be preserved when possible (for deterministic
error ordering) but a conformant implementation MUST NOT rely on a specific
iteration order for correctness.

---

## 4. Schema document format

### 4.1 YAML grammar

A Templane schema document is a YAML mapping whose keys are field names:

```yaml
name:
  type: string
  required: true
age:
  type: number
  required: false
status:
  type: enum
  values: [active, inactive, pending]
  required: true
tags:
  type: list
  items:
    type: string
address:
  type: object
  required: true
  fields:
    city:
      type: string
      required: true
    country:
      type: string
```

**Defaults:**

- A field with no `type` key defaults to `string`.
- A field with no `required` key defaults to `false`.
- An `enum` field with no `values` key defaults to `[]` (all values invalid).
- A `list` field with no `items` key MUST be treated as `list<string>`.

**Errors:**

- If the YAML root is not a mapping (e.g. a scalar or sequence), the parser
  MUST return a result containing the error string exactly:
  `"Schema must be a YAML mapping"`.
- Any YAML parse error MUST be returned as a result error, never as an
  exception.

### 4.2 Template body separator (embedded mode)

A Templane schema document MAY include a template body after the separator
`"\n---\n"`. The schema parser MUST emit both the parsed schema AND the
verbatim body string (including trailing newline if present):

```
name:
  type: string
  required: true
---
Hello {{ name }}!
```

The body's template syntax is defined by the consuming engine, not by Templane.

### 4.3 Sidecar mode

A Templane schema document MAY instead reference an **external** body file
via the reserved top-level key `body`, leaving the template content in its
native format (`.jinja`, `.hbs`, `.ftl`, `.tmpl`, `.md`, etc.) untouched:

```yaml
body: ./email.jinja
engine: jinja
user:
  type: object
  required: true
  fields:
    name: { type: string, required: true }
```

**Reserved top-level keys** (1.1):

| Key | Type | Required | Meaning |
|---|---|---|---|
| `body` | string | no | Relative path to an external body file. |
| `engine` | enum | no | One of `jinja`, `handlebars`, `freemarker`, `gotemplate`, `markdown`, `html-raw`, `yaml-raw`. |

When either key is present at the top of the YAML mapping, the schema parser
MUST NOT treat it as a field definition. All other top-level keys remain
field definitions as in §4.1.

**Body path resolution (normative):**

- `body` MUST be a **relative** path. Absolute paths (starting with `/`)
  and paths containing `..` that escape the schema's directory MUST cause
  the parser to return a result error: `"body path must be relative and
  inside the schema's directory"`.
- Resolution is relative to the directory of the schema file being parsed.
- If the resolved path does not exist at load time, the parser MUST return
  a result error: `"body file not found: <resolved-path>"`.
- The parser MUST reject schemas that contain BOTH a `body:` key AND a
  `"\n---\n"` separator, returning the error: `"cannot use both 'body:'
  key and '---' separator"`.

**Engine inference (normative):**

If `engine:` is absent and `body:` is present, implementations MAY infer
the engine from the body file extension using this mapping:

| Extension | Inferred engine |
|---|---|
| `.jinja`, `.jinja2`, `.j2` | `jinja` |
| `.hbs`, `.handlebars` | `handlebars` |
| `.ftl`, `.ftlh` | `freemarker` |
| `.tmpl`, `.gotmpl` | `gotemplate` |
| `.md`, `.markdown` | `markdown` |
| `.html`, `.htm` | `html-raw` |
| `.yaml`, `.yml` | `yaml-raw` |

If the extension is unknown or missing, `engine:` MUST be set explicitly.

If both `engine:` and an inferrable extension are present and they
disagree, the explicit `engine:` value wins; no warning is required.

**Check-only mode:**

A schema with neither `body:` key nor `---` separator is a **check-only**
schema — valid, parseable, renders nothing. It exists to validate data
files against a schema without producing output. The parser MUST emit
the schema with a `null` / absent body.

**Wire format changes (§5.1):** the wire format's TypedSchema gains two
optional keys: `body_path` (the resolved relative path, if sidecar) and
`engine` (the declared or inferred engine, if known). The conform adapter
protocol continues to ship `{schema, body}` with `body` already resolved
to its string contents — implementations are not expected to do file I/O
across the wire.

---

## 5. Wire format (JSON)

### 5.1 TypedSchema

```json
{
  "id": "user-profile",
  "fields": {
    "name": {
      "name": "name",
      "type": {"kind": "string"},
      "required": true
    },
    "status": {
      "name": "status",
      "type": {"kind": "enum", "values": ["active", "inactive"]},
      "required": true
    }
  }
}
```

### 5.2 TemplaneField

```json
{"name": "<string>", "type": <TemplaneFieldType>, "required": <boolean>}
```

### 5.3 TemplaneFieldType (discriminated union)

Exactly one `kind` tag per value. Additional keys depend on kind:

```json
{"kind": "string"}
{"kind": "number"}
{"kind": "boolean"}
{"kind": "null"}
{"kind": "enum", "values": ["a", "b", "c"]}
{"kind": "list", "item_type": {"kind": "string"}}
{"kind": "object", "fields": {"<name>": <TemplaneField>, ...}}
```

### 5.4 AST (Abstract Syntax Tree)

Templates are parsed into an AST of four node types. The AST is
engine-agnostic: Handlebars, Jinja2, FreeMarker, Go templates, and others
MAP to this shape.

```json
{"kind": "text", "content": "Hello "}
{"kind": "expr", "field": "name"}
{"kind": "if",
 "condition": {"op": "==", "left": "status", "right": "active"},
 "then_branch": [<ASTNode>, ...],
 "else_branch": [<ASTNode>, ...]}
{"kind": "foreach",
 "var": "tag",
 "iterable": "tags",
 "body": [<ASTNode>, ...]}
```

**Condition:**

```json
{"op": "==", "left": "<field-path>", "right": <any>}
```

The `==` operator is the only operator REQUIRED by this specification.
Implementations MAY support additional operators as an extension.

### 5.5 TIR (Typed Intermediate Representation)

After type-checking succeeds, the IR generator walks the AST with data to
produce a TIR — a resolved AST where every expression has a known value.
TIR is the format that output adapters (HTML, YAML, etc.) consume.

```json
{"kind": "text", "content": "Hello "}
{"kind": "expr", "field": "name", "resolved": "Alice"}
{"kind": "if", "condition": true, "branch": [<TIRNode>, ...]}
{"kind": "foreach", "var": "tag", "items": [[<TIRNode>, ...], [<TIRNode>, ...]]}
```

**TIRResult** (top-level):

```json
{
  "template_id": "<string>",
  "schema_id": "<string>",
  "nodes": [<TIRNode>, ...]
}
```

The `resolved` field in TIR expression nodes **MUST** always be present in
JSON serialization, even when its value is `null`. (This is load-bearing —
`null` is distinguished from absence.)

### 5.6 TypeCheckError

```json
{"code": "<string>", "field": "<string>", "message": "<string>"}
```

Error codes are enumerated in §7.1.

---

## 6. Operations

Every conformant Templane implementation MUST expose four operations. Function
names are illustrative; implementations MAY name them idiomatically (e.g.
`parse_schema`, `parseSchema`, `ParseSchema`, etc.).

### 6.1 parse(yaml_str, schema_id) → ParseResult

**Input:** raw YAML schema document as a string, and a schema identifier.

**Output:** either `{schema: TypedSchema, body?: string}` on success, or
`{error: string}` on failure. See §4.1 for error conditions.

### 6.2 check(schema, data) → TypeCheckError[]

**Input:** a `TypedSchema` and a data mapping.

**Output:** the list of all type-check errors (possibly empty). The checker
**MUST NOT** short-circuit on the first error. See §7.

### 6.3 generate(ast, data, schema_id, template_id) → TIRResult

**Input:** an AST node list, a data mapping, and provenance identifiers.

**Output:** a `TIRResult`. See §5.5 for invariants.

- Missing paths **MUST** resolve to `null` without throwing (type-checking
  is the caller's responsibility).
- Only the `==` condition operator is required; unknown operators evaluate
  to `false`.

### 6.4 render(tir) → string (output adapter)

**Input:** a `TIRResult`.

**Output:** a string in the adapter's target format.

Implementations MUST provide at least two adapters: `html` (with entity
escaping of expression values) and `yaml` (without escaping). See §7.4.

---

## 7. Type-checking semantics

### 7.1 Error codes

| Code                       | Condition                                                    |
|----------------------------|--------------------------------------------------------------|
| `missing_required_field`   | required field absent from data                              |
| `type_mismatch`            | value's type differs from schema type                        |
| `invalid_enum_value`       | value not in the enum's `values` set                         |
| `unknown_field`            | field present in data but absent from schema                 |
| `did_you_mean`             | unknown field whose closest known name is Levenshtein ≤ 3    |

`did_you_mean` errors supersede `unknown_field` errors for the same field
when both would apply. (i.e. the checker emits one or the other, not both.)

When a required field is misspelled, both `missing_required_field` (for the
expected name) and `did_you_mean` (for the provided name) are emitted.

### 7.2 Error message format

Error messages MUST follow this exact format (fixture-verified):

| Code                      | Message template                                                                  |
|---------------------------|-----------------------------------------------------------------------------------|
| `missing_required_field`  | `Required field '<path>' is missing`                                              |
| `type_mismatch`           | `Field '<path>' expected <want>, got <got>`                                       |
| `invalid_enum_value`      | `Field '<path>' value '<v>' not in enum [<v1>, <v2>, ...]`                        |
| `unknown_field`           | `Field '<path>' is not defined in schema`                                         |
| `did_you_mean`            | `Unknown field '<path>'. Did you mean '<closest>'?`                               |

### 7.3 Path notation

Field paths use dot notation for nested objects and bracket notation for list
indices:

- `name` — top-level field
- `address.city` — nested inside object
- `tags[1]` — element at index 1 of a list
- `users[0].email` — nested through list of objects

### 7.4 Primitive type coercion rules

- `string` matches the JSON string type only.
- `number` matches both integer and floating-point JSON numbers. Booleans
  **MUST NOT** match `number` (despite some languages making booleans a
  subtype of integer).
- `boolean` matches only `true`/`false`.
- `null` matches only the JSON `null` value.
- `enum` values are matched by exact string equality.

### 7.5 Adapter semantics

**HTML adapter:**

- Text nodes (`TIRTextNode`) pass through unchanged.
- Expression nodes (`TIRExprNode`) have their `resolved` value HTML-entity-
  escaped: `&` → `&amp;`, `<` → `&lt;`, `>` → `&gt;`, `"` → `&quot;`,
  `'` → `&#x27;`.
- `null` resolves to empty string (NOT the literal "null").
- Falsy primitives (`0`, `false`, empty string) render as their string form.
- Output begins with a provenance comment:
  `<!-- templane template_id=<id> schema_id=<id> -->\n`

**YAML adapter:**

- No escaping applied.
- Otherwise identical to HTML adapter.
- Provenance comment uses `#` syntax: `# templane template_id=<id> schema_id=<id>\n`

---

## 8. Schema evolution

A Templane implementation MAY provide a `detect(old_schema, new_schema)`
operation that returns the list of breaking changes between two schemas.

### 8.1 Breaking change categories

| Category              | Condition                                                |
|-----------------------|----------------------------------------------------------|
| `removed_field`       | field in old schema, absent in new                       |
| `required_change`     | field went from optional to required                     |
| `type_change`         | field type (`kind`) changed                              |
| `enum_value_removed`  | enum value in old schema is absent from new              |

The detector MUST recurse into `object` fields, producing dotted paths
(e.g. `address.zip`).

### 8.2 Non-breaking changes

The following changes MUST NOT be reported as breaking:

- Adding a new optional field.
- Adding a new enum value.
- Changing a required field to optional.

---

## 9. Conform adapter protocol

Implementations are validated by passing all 32 fixtures in
`templane-spec/fixtures/` through a subprocess shim called a **conform adapter**.
The adapter is invoked by the `templane-conform` CLI (Node.js).

### 9.1 Transport

- Adapter process is spawned once per test run.
- `stdin` receives one JSON request per line.
- `stdout` emits one JSON response per line.
- The process MUST stay alive until stdin closes.
- UTF-8 encoding throughout.
- No startup or teardown messages — the first stdin line is the first fixture.

### 9.2 Request

```json
{"fixture_id": "<category>/<name>", "fixture": <fixture-input>}
```

Routing is by `fixture_id` prefix:

- `schema-parser/*`  → §6.1 operation
- `type-checker/*`   → §6.2 operation
- `ir-generator/*`   → §6.3 operation
- `adapters/html-*`  → HTML adapter §7.5
- `adapters/yaml-*`  → YAML adapter §7.5

### 9.3 Response

```json
{"output": <operation-result>}
```

or on error:

```json
{"output": null, "error": "<human-readable>"}
```

The CLI compares `output` to the fixture's `expected_output` using
**order-insensitive deep equality for objects**, **order-sensitive for arrays**.

### 9.4 Fixture file format

```json
{
  "fixture_id": "<category>/<name>",
  "input": <operation-input>,
  "expected_output": <expected-result>
}
```

There are exactly 32 fixtures: 8 per category × 4 categories.

---

## 10. Conformance

### 10.1 Compliance criterion

An implementation is **Templane 1.0 compliant** if and only if:

1. Its conform adapter reports 32/32 across all fixtures when run via
   `templane-conform`.
2. Its type-check error messages match the formats in §7.2 character-for-
   character (verified by fixture-specific expected outputs).
3. Its JSON serialization of TIR `expr` nodes includes the `resolved` key
   even when the value is `null`.

### 10.2 Current compliant implementations

At the time of this specification's publication, five reference
implementations exist and pass 32/32:

| Language   | Package        | Template engine integration |
|------------|----------------|------------------------------|
| Python     | `templane-spec/templane-core` | — (reference)            |
| TypeScript | `templane-ts`       | `handlebars-templane` (Handlebars) |
| Python     | `templane-python`   | `jinja_templane` (Jinja2)         |
| Java       | `templane-java`     | `freemarker-templane` (FreeMarker) |
| Go         | `templane-go`       | — (integrations pending)     |

### 10.3 Conformance testing

Run the full matrix:

```bash
node templane-spec/templane-conform/dist/cli.js \
  --adapters \
    "spec:python3 templane-spec/conform-adapter/run.py" \
    "ts:node templane-ts/dist/conform-adapter.js" \
    "py:python3 templane-python/conform-adapter/run.py" \
    "java:templane-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar" \
    "go:templane-go/bin/conform-adapter"
```

Expected:

```
Running 32 fixture(s) across 5 implementation(s)...
  ✓ spec:   32/32
  ✓ ts:     32/32
  ✓ py:     32/32
  ✓ java:   32/32
  ✓ go:     32/32
All implementations conformant.
```

---

## 11. References

- **RFC 2119** — Key words for use in RFCs to Indicate Requirement Levels.
- **YAML 1.2** — Canonical YAML specification. https://yaml.org/spec/1.2.2/
- **JSON** — RFC 8259.
- **LSP** — Language Server Protocol. https://microsoft.github.io/language-server-protocol/
- **Jinja2** — https://jinja.palletsprojects.com/
- **Handlebars** — https://handlebarsjs.com/
- **FreeMarker** — https://freemarker.apache.org/

---

## Appendix A — Example schemas

### A.1 User profile

```yaml
name:
  type: string
  required: true
email:
  type: string
  required: true
age:
  type: number
status:
  type: enum
  values: [active, inactive, pending]
  required: true
tags:
  type: list
  items:
    type: string
address:
  type: object
  fields:
    city:
      type: string
      required: true
    country:
      type: string
```

### A.2 Invoice

```yaml
invoice_id:
  type: string
  required: true
total:
  type: number
  required: true
paid:
  type: boolean
  required: true
line_items:
  type: list
  required: true
  items:
    type: object
    fields:
      sku:
        type: string
        required: true
      quantity:
        type: number
        required: true
```

---

## Appendix B — Fixture index

The 32 conformance fixtures live in `templane-spec/fixtures/`, organized by category.

### B.1 schema-parser (8)

`basic`, `required-fields`, `enum-type`, `list-type`, `object-type`,
`body-extracted`, `invalid-schema`, `deep-nesting`.

### B.2 type-checker (8)

`valid-data`, `missing-required`, `type-mismatch`, `invalid-enum`,
`unknown-field`, `did-you-mean`, `nested-object`, `list-type-mismatch`.

### B.3 ir-generator (8)

`basic-expr`, `missing-path`, `if-true`, `if-false`, `foreach-items`,
`nested-path`, `condition-equals`, `provenance`.

### B.4 adapters (8)

`html-basic`, `html-special-chars`, `html-provenance`, `yaml-basic`,
`yaml-provenance`, `html-falsy-zero`, `html-foreach`, `yaml-foreach`.

---

*This specification is released under the Apache License, Version 2.0.*
