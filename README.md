<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="brand/svg/mark-reverse.svg">
    <img src="brand/svg/mark-primary.svg" alt="templane" width="128" height="128"/>
  </picture>
</p>

<h1 align="center">templane</h1>

<p align="center"><em>A protocol for typed template contracts, conformance, and cross-engine rendering.</em></p>

<p align="center"><strong>Templates have lacked a shared typed contract layer for decades. Templane is an attempt to fix that.</strong></p>

<p align="center">
  <a href="SPEC.md#10-conformance"><img src="https://img.shields.io/badge/conformance-5%20%C3%97%2040%2F40-brightgreen" alt="Conformance"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License"/></a>
</p>

---

## Why This Exists

Templates are one of the most widely deployed layers in software, and one of
the least typed.

Jinja2, Handlebars, FreeMarker, Go templates, ERB, Liquid, Mustache, and
similar engines all rely on the same fragile contract: pass in a bag of values,
look fields up by string name, and hope the data matches what the template
expects. When it does not, the usual result is not a compile-time error. It is
blank output, degraded rendering, or a bug that surfaces days later.

The missing piece is simple: the shape of template data is usually nowhere
declared as a real contract.

Templane adds that contract layer.

---

## What Templane Is

Templane is a **protocol**, not a replacement template engine.

It defines:

1. A YAML schema format for describing template input data.
2. A shared set of operations: `parse`, `check`, `generate`, `render`.
3. A conformance model based on shared fixtures and a common adapter protocol.
4. A breaking-change model for schema evolution.

Templane sits above existing engines. Your Jinja, Handlebars, FreeMarker, or Go
template files stay in their native syntax. Templane adds typed schema
contracts, validation, and cross-language consistency around them.

---

## What Templane Is Not

- Not a new templating language.
- Not a replacement for Jinja2, Handlebars, FreeMarker, or Go templates.
- Not a single shared runtime used by every language.
- Not just a validation library at an API boundary.

Each implementation is native to its own language. What is shared is the
protocol, the fixture suite, and the conformance rules.

---

## Core Concepts

- **Schema**
  A YAML contract describing the fields, types, optionality, enums, and nesting
  expected by a template.

- **Sidecar schema**
  The recommended form: a `.schema.yaml` file next to an existing native
  template file, referenced with `body: ./template.ext`.

- **Engine binding**
  A language-specific integration that validates data before handing it to the
  native engine.

- **`parse`**
  Read a schema document and turn it into a typed schema model.

- **`check`**
  Validate data against the schema and collect all errors.

- **`generate`**
  Produce typed intermediate representation (TIR) from template structure plus
  validated data.

- **`render`**
  Produce output through an adapter or engine-specific binding.

- **Conformance**
  Every implementation must match the same shared fixture suite.

- **Breaking-change detection**
  Schema diffs are classified into protocol-level breaking categories such as
  `removed_field`, `required_change`, `type_change`, and `enum_value_removed`.

---

## Quick Example

Existing template:

```jinja
Hello {{ user.name }}!
{% if user.status == "active" %}Premium member.{% endif %}
Balance: ${{ account.balance }}
```

Sidecar schema placed beside it:

```yaml
body: ./greeting.jinja
engine: jinja

user:
  type: object
  required: true
  fields:
    name: { type: string, required: true }
    status:
      type: enum
      values: [active, inactive, pending]
      required: true
account:
  type: object
  required: true
  fields:
    balance: { type: number, required: true }
```

Python usage:

```python
from jinja_templane import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment("./templates")
template = env.get_template("greeting.schema.yaml")

try:
    output = template.render(
        user={"name": "Alice", "status": "actve"},
        account={"blance": 100},
    )
except TemplaneTemplateError as exc:
    for err in exc.errors:
        print(f"[{err.code}] {err.message}")
```

Example failures:

```text
[invalid_enum_value] Field 'user.status' value 'actve' not in enum [active, inactive, pending]
[did_you_mean] Unknown field 'account.blance'. Did you mean 'balance'?
[missing_required_field] Required field 'account.balance' is missing
```

---

## Repository Layout

```text
Templane/
├── README.md                  product overview
├── SPEC.md                    normative protocol and schema reference
├── docs/                      guides and workflow documentation
├── examples/                  cross-cutting examples
├── templane-spec/             protocol hub, fixtures, conform runner, reference impl
├── templane-python/           Python implementation + Jinja binding
├── templane-ts/               TypeScript implementation + Handlebars binding + xt CLI
├── templane-java/             Java implementation + FreeMarker binding
├── templane-go/               Go implementation
└── brand/                     logos and visual assets
```

---

## Implementations

| Language | Package | Engine integration | Conformance | Tests |
|---|---|---|:---:|:---:|
| Python | [`templane-spec/templane-core`](templane-spec/) | reference implementation | 40/40 | 55 |
| TypeScript | [`templane-ts`](templane-ts/) | Handlebars | 40/40 | 81 |
| Python | [`templane-python`](templane-python/) | Jinja2 | 40/40 | 75 |
| Java | [`templane-java`](templane-java/) | FreeMarker | 40/40 | 65 |
| Go | [`templane-go`](templane-go/) | stdlib integration pattern | 40/40 | 56 |

Total today: **5 implementations, 40 fixtures, 200 conformance passes, 332 unit tests.**

---

## Conformance Model

Templane does **not** ship one shared core library that every language wraps.

Instead:

- `templane-spec/templane-core` is the **reference implementation**
- each language reimplements the protocol natively
- `templane-conform` runs every implementation against the same fixture suite
- parity is proven by fixture agreement, not by sharing one runtime

This is the central design decision of the repo. Templane is a protocol with
multiple conforming implementations, not a single implementation with language
bindings on top.

---

## Current Status

Current repo state:

- The protocol spec, fixture suite, conform runner, and five implementations are
  present in the repository.
- The conformance model is central and explicit.
- Sidecar schemas are the intended user-facing direction.

Areas that still require disciplined validation before broader claims should be
treated as final:

- exact package/distribution readiness per language
- consistency of CLI behavior versus docs
- consistency of schema-form messaging across all docs
- end-to-end first-run experience from a clean machine

If you are evaluating the repo locally, start with the testing and baseline
docs rather than assuming every README reflects final packaging status.

---

## Getting Started

If you want to use or evaluate Templane locally:

- Start with the protocol and repo guides:
  - [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)
  - [docs/BASELINE_CHECKLIST.md](docs/BASELINE_CHECKLIST.md)
- Then choose an implementation:
  - [templane-python/README.md](templane-python/README.md)
  - [templane-ts/README.md](templane-ts/README.md)
  - [templane-java/README.md](templane-java/README.md)
  - [templane-go/README.md](templane-go/README.md)

To run the full conformance matrix from repo root:

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

```text
Running 40 fixture(s) across 5 implementation(s)...
  ✓ spec:   40/40
  ✓ ts:     40/40
  ✓ py:     40/40
  ✓ java:   40/40
  ✓ go:     40/40
All implementations conformant.
```

---

## Documentation Map

- [SPEC.md](SPEC.md)
  Normative protocol and schema reference.

- [docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)
  Local setup, testing order, and repo walkthrough.

- [docs/ADOPTION.md](docs/ADOPTION.md)
  How to add Templane to an existing codebase.

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
  Internal architecture, conformance flow, and implementation model.

- [docs/BASELINE_CHECKLIST.md](docs/BASELINE_CHECKLIST.md)
  Current baseline contract for local validation from zero.

- [examples/](examples/)
  Cross-cutting examples and adoption scenarios.

- [.github/workflows/README.md](.github/workflows/README.md)
  CI/CD workflows, release paths, and publishing prerequisites for maintainers.

---

## Contributing

Templane is protocol-first. That means behavior changes carry a high bar.

If you change protocol semantics, you should expect to update:

- [SPEC.md](SPEC.md)
- fixtures under `templane-spec/fixtures/`
- the reference implementation
- every conforming language implementation

Start here:

- [CONTRIBUTING.md](CONTRIBUTING.md)

---

## License

Apache License 2.0. See [LICENSE](LICENSE).
