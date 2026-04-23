# TSP — Template Schema Protocol

**Compile-time schema safety for templating engines.**

TSP brings type checking, schema validation, and schema-evolution detection to
Jinja2, Handlebars, FreeMarker, Go templates, and any other templating engine.
It is to templates what LSP is to editors: a protocol that decouples the
intelligence (schema layer) from the host (templating engine).

[![Conformance](https://img.shields.io/badge/conformance-5%20×%2032%2F32-brightgreen)](SPEC.md#10-conformance)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)

---

## The problem TSP solves

Every mainstream templating engine shares the same flaw: **templates are
untyped contracts with zero compile-time enforcement.**

```jinja
{# Jinja2 template — no schema, no type safety #}
Hello {{ user.naem }}!              {# typo — renders blank #}
{% if user.status == "actve" %}     {# typo — always false #}
  Premium member
{% endif %}
Your balance: ${{ account.blance }}  {# typo — renders blank #}
```

Problems this causes (all silent at runtime):

1. Misspelled field names — render blank, no error.
2. Wrong type passed — cryptic stack trace somewhere downstream.
3. Missing required field — blank output, nobody notices.
4. Enum value typo — `status: "actve"` passes through unchallenged.
5. Nested path error — `user.address.zpi.code` fails at depth.
6. Breaking schema change — backend renames a field; every template silently breaks.
7. No IDE support — no autocomplete, no hover docs, no schema awareness.

---

## How TSP fixes it

Declare the data shape alongside the template:

```
name:
  type: string
  required: true
status:
  type: enum
  values: [active, inactive, pending]
  required: true
account:
  type: object
  required: true
  fields:
    balance:
      type: number
      required: true
---
Hello {{ name }}!
{% if status == "active" %}
  Premium member
{% endif %}
Your balance: ${{ account.balance }}
```

Now the TSP type checker catches every error before render time:

```python
from jinja_tsp.environment import TSPEnvironment, TSPTemplateError

env = TSPEnvironment("./templates")
template = env.get_template("greeting.tsp")

try:
    template.render(name="Alice", status="actve", account={"blance": 100})
except TSPTemplateError as e:
    for err in e.errors:
        print(f"[{err.code}] {err.message}")
```

Output:

```
[invalid_enum_value]    Field 'status' value 'actve' not in enum [active, inactive, pending]
[did_you_mean]          Unknown field 'blance'. Did you mean 'balance'?
[missing_required_field] Required field 'account.balance' is missing
```

---

## Implementations

Five reference implementations, all **32/32** on the conformance suite.

| Language   | Package        | Engine integration | Conformance | Tests |
|------------|----------------|--------------------|:-----------:|:-----:|
| Python     | [`tsp-spec/tsp-core`](tsp-spec/) | — (reference) | ✓ 32/32 | 42 |
| TypeScript | [`tsp-ts`](tsp-ts/) | [`handlebars-tsp`](tsp-ts/src/handlebars-tsp.ts) (Handlebars) | ✓ 32/32 | 64 |
| Python     | [`tsp-python`](tsp-python/) | [`jinja_tsp`](tsp-python/src/jinja_tsp/) (Jinja2) | ✓ 32/32 | 59 |
| Java       | [`tsp-java`](tsp-java/) | [`freemarker-tsp`](tsp-java/freemarker-tsp/) (FreeMarker) | ✓ 32/32 | 49 |
| Go         | [`tsp-go`](tsp-go/) | — (integrations pending) | ✓ 32/32 | 43 |

**Total:** 5 × 32 = **160 fixture passes**, **257 unit tests**.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                tsp-spec (the hub)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ 32 fixtures  │  │ tsp-conform  │  │  tsp-core    │   │
│  │ (JSON)       │  │ CLI (Node)   │  │  (Python ref)│   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────┘
                 ▲
                 │ conform adapter protocol
                 │ (line-delimited JSON over stdin/stdout)
                 │
    ┌────────────┼──────────────┬──────────────┬──────────────┐
    ▼            ▼              ▼              ▼              ▼
┌────────┐  ┌────────┐   ┌──────────┐   ┌─────────┐   ┌────────┐
│ tsp-ts │  │ tsp-py │   │ tsp-java │   │ tsp-go  │   │  ...   │
│   TS   │  │ Python │   │ Java 21  │   │  Go     │   │ future │
└────────┘  └────────┘   └──────────┘   └─────────┘   └────────┘
```

Every implementation:

1. Parses YAML schemas into a `TypedSchema`.
2. Type-checks user data, emitting structured `TypeCheckError`s.
3. Walks an AST with data to produce a Typed Intermediate Representation.
4. Renders TIR to HTML/YAML/other formats via adapters.

The conform adapter protocol (line-delimited JSON over stdin/stdout) lets
`tsp-conform` test every implementation against the same 32 fixtures,
guaranteeing semantic equivalence across languages.

See [SPEC.md](SPEC.md) for the normative specification.

---

## Quick start

Pick your language:

### Python + Jinja2

```bash
cd tsp-python
uv sync --extra dev
```

```python
from jinja_tsp.environment import TSPEnvironment

env = TSPEnvironment("./templates")
t = env.get_template("hello.tsp")
print(t.render(name="World"))
```

### TypeScript + Handlebars

```bash
cd tsp-ts
npm install && npm run build
```

```typescript
import { compile } from './src/handlebars-tsp';

const tmpl = compile(templateSource, 'hello');
console.log(tmpl.render({ name: 'World' }));
```

Or use the `xt` CLI:

```bash
node tsp-ts/dist/xt.js render hello.hbs data.json
```

### Java + FreeMarker

```bash
cd tsp-java
./gradlew publishToMavenLocal   # installs to ~/.m2/repository/dev/tsp/
```

```java
TSPConfiguration cfg = new TSPConfiguration(Paths.get("./templates"));
TSPTemplate t = cfg.getTemplate("hello.tsp");
System.out.println(t.render(Map.of("name", "World")));
```

### Go

```bash
cd tsp-go
go build ./...
go test ./...
```

```go
import "tsp-go/core"

schema := core.ParseSchema(yamlSrc, "my-schema").Schema
errors := core.Check(schema, data)
```

---

## Running the conformance suite

From the repo root:

```bash
node tsp-spec/tsp-conform/dist/cli.js \
  --adapters \
    "spec:python3 tsp-spec/conform-adapter/run.py" \
    "ts:node tsp-ts/dist/conform-adapter.js" \
    "py:python3 tsp-python/conform-adapter/run.py" \
    "java:tsp-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar" \
    "go:tsp-go/bin/conform-adapter"
```

Expected output:

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

## Documentation

- **[SPEC.md](SPEC.md)** — Normative protocol specification (type system, wire
  format, operations, conformance).
- **[tsp-spec/](tsp-spec/)** — Conformance fixtures + Python reference impl.
- **[tsp-ts/](tsp-ts/)** — TypeScript implementation + Handlebars integration + `xt` CLI.
- **[tsp-python/](tsp-python/)** — Python implementation + Jinja2 integration + breaking-change detector.
- **[tsp-java/](tsp-java/)** — Java 21 implementation + FreeMarker integration.
- **[tsp-go/](tsp-go/)** — Go implementation + breaking-change detector.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — How to add a new language binding or extend the protocol.

---

## Comparison to adjacent technologies

|                        | TSP        | JSON Schema | Protobuf    | Avro        |
|------------------------|------------|-------------|-------------|-------------|
| Domain                 | templates  | data        | wire messages | data      |
| Schema language        | YAML       | JSON        | .proto IDL  | JSON        |
| Template-engine-agnostic | ✓        | n/a         | n/a         | n/a         |
| Compile-time checking  | ✓          | ✗ (runtime) | ✓           | ✓           |
| Schema evolution rules | ✓ (§8)     | partial     | ✓           | ✓           |
| Conformance suite      | 32 fixtures | ~1000 tests | protos/     | interop/    |

TSP is **not** a competitor to JSON Schema or Protobuf — it solves a different
problem (type-safe templating). But it borrows their playbook for rigor:
versioned spec, fixture-based conformance, multiple language bindings.

---

## License

Apache License 2.0. See [LICENSE](LICENSE).
