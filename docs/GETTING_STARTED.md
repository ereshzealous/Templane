# Getting Started

Zero to `5 × 40/40` in about 10 minutes. This doc walks through **every
piece** of the Templane monorepo — how to install, how to build, how to
test, and how each language's pieces fit together.

- [0. What you're looking at](#0-what-youre-looking-at)
- [1. Prerequisites](#1-prerequisites)
- [2. Clone + bootstrap](#2-clone--bootstrap)
- [3. Per-language test runs](#3-per-language-test-runs)
- [4. Cross-implementation conformance (the 5 × 40/40 matrix)](#4-cross-implementation-conformance-the-5--4040-matrix)
- [5. Run the examples](#5-run-the-examples)
- [6. What is Core? CLI? Python? Java? TypeScript? Go?](#6-what-is-core-cli-python-java-typescript-go)
- [7. The two CLIs explained](#7-the-two-clis-explained)
- [8. Using Templane in your own code](#8-using-templane-in-your-own-code)
- [9. Where to go from here](#9-where-to-go-from-here)

---

## 0. What you're looking at

Templane is a **language-neutral protocol** with **five reference
implementations** that all behave identically — proven by a shared
conformance suite.

```
Templane/
├── SPEC.md                        normative spec (RFC 2119)
├── README.md                      pitch + story
├── docs/
│   ├── GETTING_STARTED.md         ← you are here
│   ├── ADOPTION.md                how to drop Templane into an existing codebase
│   └── ARCHITECTURE.md            12 Mermaid diagrams of how it's wired
│
├── templane-spec/                 the hub — spec, reference impl, CLI, fixtures
│   ├── templane-core/             Python reference implementation (the "spec")
│   ├── templane-conform/          Node CLI that runs the conformance matrix
│   ├── conform-adapter/           reference's stdio bridge
│   └── fixtures/                  40 JSON fixtures — the ground truth
│
├── templane-python/               Python production impl + jinja_templane binding
├── templane-ts/                   TypeScript impl + handlebars-templane + xt CLI
├── templane-java/                 Java 21 impl + freemarker-templane binding
├── templane-go/                   Go impl (stdlib text/template integration)
│
├── examples/                      6 progressive root examples (hello → Helm)
└── brand/                         icons, palette, social card
```

**Two CLIs** (covered in §7):
- `templane-conform` — tests that any implementation agrees with the 40 fixtures. Only needed when you're building a Templane implementation.
- `xt` — TypeScript-side developer CLI. Today, `render` and `check` are the clearest sidecar-schema workflows; other subcommands still reflect earlier inline-body behavior.

---

## 1. Prerequisites

You need all five toolchains to run the full matrix. You only need **one**
to build and test a single language.

| Tool | Version tested | Install |
|---|---|---|
| Python | 3.12+ (3.14 works) | `brew install python@3.12` |
| uv | 0.4+ | `brew install uv` — Python package manager used by templane-python/spec |
| Node.js | 20+ (24 works) | `brew install node` or via nvm |
| Java | 21 (Temurin) | `brew install openjdk@21` |
| Gradle | (uses wrapper — don't install system Gradle) | Comes with the repo |
| Go | 1.22+ (1.26 works) | `brew install go` |

**Important**: templane-java uses Gradle 8.5 via `./gradlew`. **Don't use system `gradle`** — Shadow plugin 8.1.1 is incompatible with Gradle 9+.

---

## 2. Clone + bootstrap

```bash
git clone git@github.com:ereshzealous/Templane.git
cd Templane
```

Each language bootstraps independently. You can do them in any order or all in parallel.

### Python reference (`templane-spec/templane-core`)

```bash
cd templane-spec/templane-core
uv sync --extra dev
# → Creates .venv/ with Python 3.12 + deps + pytest
```

### Python production (`templane-python`)

```bash
cd templane-python
uv sync --extra dev
# → Creates .venv/ with Jinja2 + deps
```

### TypeScript (`templane-ts`) — includes xt CLI

```bash
cd templane-ts
npm install
npm run build
# → Produces dist/ with compiled JS
```

### TypeScript conformance CLI (`templane-spec/templane-conform`)

```bash
cd templane-spec/templane-conform
npm install
npm run build
# → Produces dist/cli.js — the templane-conform binary
```

### Java (`templane-java`)

```bash
cd templane-java
./gradlew build
# → Compiles every Gradle subproject + runs its tests
# First run downloads Gradle 8.5 + dependencies (~5 min)
# Subsequent runs cached in ~1 min
```

### Go (`templane-go`)

```bash
cd templane-go
go build ./...
# → Fast. No network if Go modules cached.
```

---

## 3. Per-language test runs

After bootstrap, every language can be tested independently.

| Language | Command (from repo root) | Expected tests | Roughly |
|---|---|:-:|---|
| Python reference | `(cd templane-spec/templane-core && .venv/bin/pytest)` | 55 | <1 s |
| Python production | `(cd templane-python && .venv/bin/pytest)` | 75 | <1 s |
| TypeScript | `(cd templane-ts && npm test)` | 81 | 2 s |
| Java | `(cd templane-java && ./gradlew test)` | 65 | 5 s (cached) |
| Go | `(cd templane-go && go test ./...)` | 56 | 2 s |

**Total across all impls: 332 unit tests.** You can run them all in
series from the repo root:

```bash
(cd templane-spec/templane-core && .venv/bin/pytest) && \
(cd templane-python && .venv/bin/pytest) && \
(cd templane-ts && npm test) && \
(cd templane-java && ./gradlew test) && \
(cd templane-go && go test ./...)
```

If any count doesn't match, it's a bug — open an issue with the output.

---

## 4. Cross-implementation conformance (the 5 × 40/40 matrix)

**This is the proof.** Five totally independent implementations run
against the same 40 fixtures. If they all pass, they agree on every
edge case the protocol specifies.

### One-time adapter builds

Each implementation ships a `conform-adapter` — a small program that
reads fixture inputs on stdin and emits results on stdout. You build
each once:

```bash
# From repo root
(cd templane-ts && npm run build)                                    # ts adapter
(cd templane-java && ./gradlew :conform-adapter:shadowJar)           # java fat JAR
(cd templane-go && go build -o bin/conform-adapter ./cmd/conform-adapter/)
# Python adapters (ref + prod) are scripts — no build step needed
# The templane-conform CLI itself must be built too (see §2)
```

### Run the matrix

```bash
node templane-spec/templane-conform/dist/cli.js \
  --adapters \
    "spec:python3 templane-spec/conform-adapter/run.py" \
    "ts:node templane-ts/dist/conform-adapter.js" \
    "py:python3 templane-python/conform-adapter/run.py" \
    "java:templane-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar" \
    "go:templane-go/bin/conform-adapter"
```

**Expected output:**

```
Running 40 fixture(s) across 5 implementation(s)...

  ✓ spec:   40/40
  ✓ ts:     40/40
  ✓ py:     40/40
  ✓ java:   40/40
  ✓ go:     40/40

All implementations conformant.
```

40 × 5 = **200 cross-impl passes**. Every impl handles every fixture
identically.

### When a fixture fails

The CLI prints the fixture id, the expected output, and the actual
output side-by-side. That's the whole debug story. The fixture file
under `templane-spec/fixtures/<category>/<name>.json` is the source of
truth.

---

## 5. Run the examples

Each language has runnable examples under its own `examples/` dir.
Plus there are 6 cross-cutting root examples.

### Python examples (6)

```bash
cd templane-python
for d in examples/*/; do
  echo "=== $d ==="
  .venv/bin/python "$d/run.py"
done
```

Covers: hello, validation errors, nested/lists, Jinja binding, breaking-change detection, external-body schema.

### TypeScript examples (5)

```bash
cd templane-ts
for d in examples/*/; do
  echo "=== $d ==="
  ./node_modules/.bin/tsx "$d/run.ts"
done
```

Covers: hello, validation errors, nested/lists, Handlebars binding, external-body schema.

### Java examples (6)

```bash
cd templane-java
for cls in Hello ValidationErrors NestedAndLists FreemarkerBinding BreakingChanges SidecarInvoice; do
  echo "=== $cls ==="
  ./gradlew -q :examples:runExample -Pmain=dev.templane.examples.$cls
done
```

Covers: hello, validation errors, nested/lists, FreeMarker binding, breaking-change detection, external-body schema.

### Go examples (5)

```bash
cd templane-go
for d in examples/*/; do
  echo "=== $d ==="
  (cd "$d" && go run .)
done
```

Covers: hello, validation errors, nested/lists, breaking-change detection, external-body schema.

### Root examples (6)

Located under `examples/` at the repo root. Use the `xt` CLI
(shipped with templane-ts):

```bash
# From repo root, after `cd templane-ts && npm run build`
node templane-ts/dist/xt.js check \
  examples/01-basic-hello/greeting.templane \
  examples/01-basic-hello/data.json
```

Each example dir has its own README with exact commands.

---

## 6. What is Core? CLI? Python? Java? TypeScript? Go?

### Core

**`templane-spec/`** is *the core of the protocol* — not a separate component but the collection of three things every implementation is measured against:

- **Specification** (`SPEC.md`): 700 lines, RFC 2119 keywords, defines type system, wire format, operations, error codes, conformance criteria.
- **Reference implementation** (`templane-spec/templane-core/`): Python implementation of the spec. Simple, readable, correct. Not meant for production — meant to settle arguments about what the spec means.
- **Fixtures** (`templane-spec/fixtures/`): 40 JSON files covering schema parsing, type checking, IR generation, and adapter rendering. Each is `{fixture_id, input, expected_output}`.

All other implementations are measured by whether their conform-adapter emits the same output as `expected_output` for each fixture.

### Python (`templane-python/`)

Production Python implementation. Differs from the reference in two ways:

1. Ships `jinja_templane` — a **Jinja2 engine binding** that wraps Jinja2's `Environment` and adds type-checking before rendering.
2. Ships extras the reference doesn't: breaking-change detector, schema hash, html/yaml adapters.

Primary public API:

```python
from jinja_templane import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment("./templates")
tmpl = env.get_template("welcome.schema.yaml")
html = tmpl.render(user={...}, subscription="pro")
```

Tests: 75. See [`templane-python/README.md`](../templane-python/README.md) and [`templane-python/examples/`](../templane-python/examples/).

### Java (`templane-java/`)

Java 21 multi-module Gradle project. Uses **sealed interfaces** for the type system, **records** for immutable models, and **Jackson polymorphic JSON** for serialization.

Subprojects:
- `templane-core/` — parser, type checker, IR generator, breaking-change detector
- `templane-adapter-html/` — HTML adapter (entity escaping)
- `templane-adapter-yaml/` — YAML adapter
- `freemarker-templane/` — FreeMarker engine binding
- `conform-adapter/` — the stdio bridge (built as a fat JAR via Shadow)
- `examples/` — 6 runnable examples with a `runExample` task

Primary public API:

```java
import dev.templane.freemarker.*;

TemplaneConfiguration cfg = new TemplaneConfiguration(Path.of("templates"));
TemplaneTemplate tmpl = cfg.getTemplate("invoice.schema.yaml");
String output = tmpl.render(Map.of("invoice_number", "INV-0042", ...));
```

Tests: 65. See [`templane-java/README.md`](../templane-java/README.md).

### TypeScript / JS (`templane-ts/`)

TypeScript implementation compiled to CommonJS. Two notable artifacts:

1. `handlebars-templane` — Handlebars engine binding
2. `xt` — the user-facing developer CLI (render/check/test/dev/build)

Primary public API (library):

```typescript
import { compile, compileFromPath, TemplaneHandlebarsError } from 'handlebars-templane';

// Embedded-mode compile from a string
const tmpl = compile(source, 'greeting');

// Load from a file path (follows body: → external body file)
const tmpl2 = await compileFromPath('templates/welcome.schema.yaml');
const html = tmpl2.render({ user: {...}, subscription: 'pro' });
```

Primary CLI (installed as a `bin` when the package is published):

```bash
xt render template.templane data.json     # render to stdout
xt check  template.templane data.json     # validate only; exit 1 on error
xt test   templates/                      # compile all schemas in a dir
xt dev    template.templane data.json     # watch + re-render on change
xt build  templates/ --out dist/bundle.js # precompile bodies to a CJS module
```

Tests: 81. See [`templane-ts/README.md`](../templane-ts/README.md).

### Go (`templane-go/`)

Go implementation. No dependency on a runtime — the conform-adapter is a single static binary (~3 MB). No engine binding ships yet (`gotmpltemplane` planned); examples use the standard library `text/template` directly after type-checking via Templane.

Primary public API:

```go
import (
    "text/template"
    "github.com/ereshzealous/Templane/templane-go/core"
)

r := core.LoadSchemaFromPath("templates/service.schema.yaml")
if r.Error != "" { log.Fatal(r.Error) }
if errs := core.Check(r.Schema, data); len(errs) > 0 { /* refuse */ }
tmpl := template.Must(template.New("svc").Parse(*r.Body))
tmpl.Execute(os.Stdout, data)
```

Tests: 56. See [`templane-go/README.md`](../templane-go/README.md).

---

## 7. The two CLIs explained

### `templane-conform` — the conformance runner

**Who needs it**: people building a Templane implementation in a new language.

**Where**: `templane-spec/templane-conform/` — a Node/TypeScript program.

**What it does**: spawns one or more conform-adapter subprocesses (one per implementation), sends each of the 40 fixtures over stdin, diffs the adapter's stdout against the expected output, reports pass/fail.

```bash
node templane-spec/templane-conform/dist/cli.js \
  --adapters "spec:python3 templane-spec/conform-adapter/run.py"
```

You can run it against a subset of adapters; the matrix in §4 is the full 5-impl run.

### `xt` — the developer CLI

**Who needs it**: anyone rendering Templane schemas day-to-day.

**Where**: in the current repo, run via `node templane-ts/dist/xt.js`.

**What it does**: one-shot operations on template/schema inputs. In the current
repo, `render` and `check` are the clearest schema-driven commands; `test`,
`dev`, and `build` still reflect earlier `.hbs` / inline-body workflows.

Full usage:

```
xt render <schema-or-inline-template> <data.json>
  Render template with data; write output to stdout.
  Exits 1 with validation errors if data doesn't match schema.
  Current implementation expects the data file to be a JSON object.

xt check <schema-or-inline-template> <data.json>
  Validate data against template schema. Exit 0 if ok, 1 on error.
  Use in CI / pre-commit hooks.

xt test <templates-dir>
  Current implementation scans `.hbs` files and optionally renders adjacent
  `.example.json` files.

xt dev <template> <data.json>
  Current implementation recompiles inline-body source on change.

xt build <templates-dir> --out <file>
  Current implementation precompiles inline-body `.hbs` files with `---`
  separators into a CommonJS module.
```

`templane-conform` is protocol infrastructure.

For `xt`, treat `render` and `check` as the verified sidecar-schema path in the
current repo. Treat `test`, `dev`, and `build` as more legacy/inline-oriented
until they are brought into alignment.

---

## 8. Using Templane in your own code

### I have existing templates. How do I add Templane?

Read [`docs/ADOPTION.md`](ADOPTION.md). The short version:

- You don't migrate templates.
- You add one `.schema.yaml` file beside each existing `.jinja` / `.hbs` / `.ftl` / `.tmpl`.
- The schema declares your data contract.
- Your binding's existing API (`env.get_template`, `compileFromPath`, `cfg.getTemplate`, `core.LoadSchemaFromPath`) follows the `body:` reference transparently.

### I'm starting from scratch. Which form?

**Default: `.schema.yaml` with `body:` pointing at a native template
file** (Jinja / Handlebars / FreeMarker / text/template). Works across
every binding. Editor-friendly. Scales from 1 template to 100.

The legacy inline form (`.templane` with `---`) still parses but
shouldn't be used for new work.

### I want to add Templane to a new language

Read [`CONTRIBUTING.md`](../CONTRIBUTING.md) and mirror one of the
existing implementations. The checklist:

1. Implement the 4 operations: `parse`, `check`, `generate`, `render`.
2. Handle both the default external-body form AND the legacy inline-body form (SPEC §4.2 + §4.3).
3. Ship a `conform-adapter` that speaks line-delimited JSON per SPEC §9.
4. Run `templane-conform --adapters <your-adapter>` and reach 40/40.
5. Add it to the cross-impl matrix in CI.

---

## 9. Where to go from here

- **Use it in an existing codebase** → [`docs/ADOPTION.md`](ADOPTION.md)
- **Understand how it's wired internally** → [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) (12 Mermaid diagrams)
- **The normative spec** → [`SPEC.md`](../SPEC.md) (RFC 2119, currently versioned at 1.0 in-repo)
- **Per-language deep-dives**:
  - [Python reference](../templane-spec/README.md)
  - [Python production](../templane-python/README.md)
  - [TypeScript + Handlebars + `xt`](../templane-ts/README.md)
  - [Java + FreeMarker](../templane-java/README.md)
  - [Go](../templane-go/README.md)
- **Per-language examples**:
  - [Python](../templane-python/examples/)
  - [TypeScript](../templane-ts/examples/)
  - [Java](../templane-java/examples/)
  - [Go](../templane-go/examples/)
- **Cross-cutting examples** (root) → [`examples/`](../examples/) — 6 progressive tiers from hello-world to Helm chart validation.
- **Release and publishing workflows** → [`.github/workflows/README.md`](../.github/workflows/README.md)
- **Java remote publish path** → [`.github/workflows/release-templane-java.yml`](../.github/workflows/release-templane-java.yml) and [Java README](../templane-java/README.md)

If anything in this document is wrong or stale, open an issue or a PR.
The walkthrough commands are tested against the HEAD of `main`.
