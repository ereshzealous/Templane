<p align="center">
  <img src="brand/final/templane-mark.svg" alt="Templane" width="160" height="160"/>
</p>

<h1 align="center">Templane</h1>

<p align="center"><em>A protocol for typed template contracts, conformance, and cross-engine rendering.</em></p>

<p align="center"><strong>Templates have been untyped for 20 years. This fixes that.</strong></p>

<p align="center">
  <a href="SPEC.md#10-conformance"><img src="https://img.shields.io/badge/conformance-5%20×%2032%2F32-brightgreen" alt="Conformance"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-blue" alt="License"/></a>
</p>

---

## The templating layer runs the world

Take a look at what's rendering right now, this second, on the internet:

- **Docs**: every Hugo, Jekyll, Sphinx, and Docusaurus site is a template pipeline.
- **Kubernetes**: every Helm chart is a Go template. Millions of clusters boot from them.
- **Infrastructure**: Terraform modules, Docker Compose files, Ansible playbooks — all templates.
- **Email**: transactional email in Ruby apps (ERB), Python apps (Jinja2), PHP apps (Twig), JS apps (Handlebars).
- **Web SSR**: Rails views, Django templates, Laravel Blade, Spring FreeMarker, Next.js metadata.
- **E-commerce**: every Shopify theme is Liquid. Every checkout.
- **SaaS**: invoice PDFs, report generation, PDF statements, compliance documents.

Templates are probably the most deployed, least inspected layer in software. And every one of these engines — **Jinja2, Handlebars, FreeMarker, ERB, Liquid, Mustache, Go templates, Pug, EJS, Blade, Smarty, Thymeleaf, Velocity** — shares the same 20-year-old blind spot:

**A template is an untyped contract.** It accepts a bag of values, looks up names by string, and substitutes them. No schema, no validation, no compile-time checking. When something is wrong, the output is silently blank or cryptically broken.

---

## A story you've probably lived

It's Tuesday. A backend engineer on a team you barely know renames a field:

```diff
- user.email
+ user.email_address
```

The rename ships. CI passes — backend tests don't know your templates exist. Staging looks fine — you didn't think to diff every email body. On Tuesday night your password-reset email template, which contains `{{ user.email }}`, starts rendering with a blank "Your email:" line.

Thursday morning, a customer complains. You grep. You find 14 other templates affected: the welcome email, the monthly invoice, the GDPR data-export notice, three internal Slack digests, and four admin dashboards. All of them have been silently degraded for four days. None of them threw an error. None of them appeared in any dashboard. Nothing alerted.

You've lived some version of this. Every team that ships templates has.

The obvious fix — "just add a test for each template!" — is where the conversation usually ends. In practice, template tests are tedious, shallow, and always out of date. And they don't catch the failure mode that matters most: the template renders *something*, so no assertion ever fails loudly.

The real problem is that **the schema of the data going into the template is nowhere written down**.

---

## The gap nobody filled

Over the last two decades, the type-safety community has methodically closed these gaps, one domain at a time:

| Year     | Domain                         | Solution                         |
|----------|--------------------------------|----------------------------------|
| 2008     | Wire formats                   | Protocol Buffers                 |
| 2009     | Event data                     | Avro                             |
| 2011     | REST APIs                      | OpenAPI / Swagger                |
| 2012+    | Code                           | TypeScript, Flow, Sorbet         |
| 2013+    | Data shapes                    | JSON Schema                      |
| 2015     | Typed RPC                      | gRPC                             |
| 2015     | API query-response             | GraphQL                          |
| 2016     | Rust's borrow checker          | Compile-time memory safety       |

Every one of those technologies is now foundational. Teams write `.proto` files without thinking. Nobody ships public APIs without an OpenAPI spec. TypeScript is the default for new frontend code.

But if you ask **"what is the type of the context dict I pass to this Jinja2 template?"** — the answer in 2026 is the same as it was in 2006:

> *Nothing. You look at the template, read the variable names, guess what types they should be, and hope.*

The last mile — where structured data becomes human-readable output — never got its typing moment. Every engine still accepts a dict and prays.

**Templane is that missing layer.**

---

## The LSP playbook

In 2015, Microsoft published the Language Server Protocol. Before LSP, adding Python support to `<your favorite editor>` required writing a Python-aware plugin for that specific editor. N editors × M languages = N×M integrations. No single maintainer could keep up.

LSP decoupled the intelligence (language server) from the host (editor). Any editor that speaks LSP gets every language. Any language server gets every editor. N + M instead of N × M.

Templane applies the identical insight to templating:

|                          | Before LSP                    | After LSP              | Before Templane                    | After Templane              |
|--------------------------|-------------------------------|------------------------|-------------------------------|------------------------|
| Host                     | Editor                        | Editor                 | Templating engine             | Templating engine      |
| Intelligence layer       | One custom plugin per (editor×language) | Language server (one per language) | None — teams write ad-hoc template tests | Templane implementation (one per language) |
| Conformance              | Ad-hoc                        | LSP spec + test harness | Ad-hoc                        | 32-fixture conform suite |

Templane doesn't replace Jinja2, Handlebars, FreeMarker, or Go templates. It sits *above* them and supplies the schema layer they never had. Any engine that adopts Templane gains compile-time type checking, structured error reporting, schema-evolution detection, and IDE tooling — with no change to the engine itself.

---

## What Templane is

Templane is a **protocol**, not a library. The protocol defines:

1. **A schema document format** (YAML) describing the shape of template data — fields, types, optionality, enums, nesting.
2. **A typed intermediate representation** (TIR) — a resolved AST where every expression has a known value. Output adapters render TIR to HTML, YAML, or any other format.
3. **Four operations** every conformant implementation provides: `parse`, `check`, `generate`, `render`.
4. **A 32-fixture conformance suite** (`templane-conform`). Any implementation that passes 32/32 is Templane 1.0 compliant. Period.
5. **A breaking-change detector** (`removed_field`, `required_change`, `type_change`, `enum_value_removed`) so the next backend field rename is caught before it ships, not four days after.

The [full specification](SPEC.md) is versioned, uses RFC 2119 keywords, and fits in one file.

---

## Show, don't tell

**Before Templane** — a Jinja2 template that will silently fail when `status` is misspelled, when `account` is missing a field, or when the backend renames anything:

```jinja
Hello {{ user.name }}!
{% if user.status == "active" %}Premium member.{% endif %}
Balance: ${{ account.balance }}
```

**After Templane** — declare the schema inline with the template:

```
user:
  type: object
  required: true
  fields:
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
Hello {{ user.name }}!
{% if user.status == "active" %}Premium member.{% endif %}
Balance: ${{ account.balance }}
```

Now bad data fails loudly, with every error collected — not just the first:

```python
from jinja_templane.environment import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment("./templates")
template = env.get_template("greeting.templane")

try:
    template.render(user={"name": "Alice", "status": "actve"},  # typo
                    account={"blance": 100})                    # typo
except TemplaneTemplateError as e:
    for err in e.errors:
        print(f"[{err.code}] {err.message}")
```

```
[invalid_enum_value]     Field 'user.status' value 'actve' not in enum [active, inactive, pending]
[did_you_mean]           Unknown field 'account.blance'. Did you mean 'balance'?
[missing_required_field] Required field 'account.balance' is missing
```

The fix happens at template-load time (in CI, at deploy time, in your editor — wherever you wire it in). Never at 2 AM when a customer complains.

---

## Implementations

Five reference implementations, all **32/32** on the conformance suite, each idiomatic to its language:

| Language   | Package        | Engine integration | Conformance | Tests |
|------------|----------------|--------------------|:-----------:|:-----:|
| Python     | [`templane-spec/templane-core`](templane-spec/) | — (reference impl) | ✓ 32/32 | 42 |
| TypeScript | [`templane-ts`](templane-ts/) | [`handlebars-templane`](templane-ts/src/handlebars-templane.ts) (Handlebars) | ✓ 32/32 | 64 |
| Python     | [`templane-python`](templane-python/) | [`jinja_templane`](templane-python/src/jinja_templane/) (Jinja2) | ✓ 32/32 | 59 |
| Java       | [`templane-java`](templane-java/) | [`freemarker-templane`](templane-java/freemarker-templane/) (FreeMarker) | ✓ 32/32 | 49 |
| Go         | [`templane-go`](templane-go/) | — (integrations pending) | ✓ 32/32 | 43 |

**Total: 5 × 32 = 160 fixture passes across 257 unit tests.** Every implementation is proven to behave identically on every edge case the protocol specifies — by running them all through the same test harness.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                templane-spec (the hub)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ 32 fixtures  │  │ templane-conform  │  │  templane-core    │   │
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
│ templane-ts │  │ templane-py │   │ templane-java │   │ templane-go  │   │  ...   │
│   TS   │  │ Python │   │ Java 21  │   │  Go     │   │ future │
└────────┘  └────────┘   └──────────┘   └─────────┘   └────────┘
```

Every implementation exposes the same four operations. The conform adapter protocol (one JSON line in, one JSON line out) lets `templane-conform` test every implementation against the same 32 fixtures, proving semantic equivalence across languages.

---

## Get started

Pick your language:

- **Python + Jinja2** → [`templane-python/README.md`](templane-python/README.md)
- **TypeScript + Handlebars** → [`templane-ts/README.md`](templane-ts/README.md) (also ships the `xt` CLI: `render`, `check`, `test`, `dev`, `build`)
- **Java + FreeMarker** → [`templane-java/README.md`](templane-java/README.md) (Gradle build, publishes to `~/.m2/repository`)
- **Go** → [`templane-go/README.md`](templane-go/README.md) (static binary, no runtime deps)
- **Adding a new language binding?** → [`CONTRIBUTING.md`](CONTRIBUTING.md)

Running the full 5-implementation conformance matrix from the repo root:

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

## What Templane is *not*

To be clear about the scope:

- **Not a replacement for Jinja2, Handlebars, or any engine.** Templane sits alongside them. Your templates keep their existing syntax. Your engine keeps its features. Templane just adds the type layer on top.
- **Not a new templating DSL.** There are enough template languages. Templane doesn't invent another one.
- **Not a runtime.** Type checking happens at template-load time (or in CI, or in your editor). At render time, Templane is out of the hot path.
- **Not opinionated about syntax.** The schema is YAML; the template body is whatever the engine wants (`{{ }}`, `${}`, `<% %>`, doesn't matter).
- **Not coupled to any ecosystem.** Every major language can host a conformant implementation. The protocol is the contract.
- **Not a validation library.** Validation libraries check user input at API boundaries. Templane checks template data at template-load time — a different phase with different ergonomics.

---

## How Templane compares to adjacent technologies

|                          | Templane         | JSON Schema   | Protobuf      | Avro          | OpenAPI       |
|--------------------------|-------------|---------------|---------------|---------------|---------------|
| Domain                   | templates   | data          | wire messages | event data    | REST APIs     |
| Schema language          | YAML        | JSON          | `.proto` IDL  | JSON          | YAML / JSON   |
| Engine-agnostic          | ✓           | n/a           | n/a           | n/a           | n/a           |
| Compile-time checking    | ✓           | ✗ (runtime)   | ✓             | ✓             | partial       |
| Schema evolution rules   | ✓ (§8)      | partial       | ✓             | ✓             | partial       |
| Conformance suite        | 32 fixtures | ~1000 tests   | `protos/`     | interop/      | parser tests  |

Templane is not a competitor to any of these. It borrows their playbook — versioned spec, fixture-based conformance, cross-language bindings — and applies it to the templating gap they don't cover.

---

## Documentation

- **[SPEC.md](SPEC.md)** — Normative protocol specification (type system, wire format, operations, conformance). Versioned. RFC 2119 keywords throughout.
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — How to extend the protocol, add a language binding, or add a new template-engine integration.
- Per-language READMEs: [spec](templane-spec/README.md), [ts](templane-ts/README.md), [py](templane-python/README.md), [java](templane-java/README.md), [go](templane-go/README.md).

---

## License

Apache License 2.0. See [LICENSE](LICENSE).

---

*Templane is the type layer the templating world should have had in 2008. We're 18 years late. Let's fix it.*
