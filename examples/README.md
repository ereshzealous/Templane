# Templane Examples

Three flavors of examples, grouped by purpose:

## 1. By mode — complex, realistic scenarios

The sharpest way to pick an example: **are you looking for embedded
mode or sidecar mode?** These dirs group complex, production-shaped
examples by mode:

| Mode | Dir | What you see there |
|---|---|---|
| **Embedded** (one file, schema + body via `---`) | [`embedded/`](embedded/) — 3 examples | Invoice with taxes and discounts, compliance audit log report, GitHub Actions workflow generator |
| **Sidecar** (schema file + native-engine body file) | [`sidecar/`](sidecar/) — 3 examples | Multi-environment Kubernetes deployment validation, order confirmation email, feature-flag config validation |

## 2. Per-language examples (in each SDK's idioms)

If you want to see how Templane feels in a specific language, start here:

| Language | Examples dir |
|---|---|
| Python (with Jinja2) | [`templane-python/examples/`](../templane-python/examples/) — 6 examples |
| TypeScript (with Handlebars) | [`templane-ts/examples/`](../templane-ts/examples/) — 5 examples |
| Java 21 (with FreeMarker) | [`templane-java/examples/`](../templane-java/examples/) — 6 examples |
| Go (with `text/template`) | [`templane-go/examples/`](../templane-go/examples/) — 5 examples |

Each dir has its own `README.md` with setup, run commands, and expected output. Examples cover: hello-world → validation errors → nested/lists → engine binding → breaking-change detection → sidecar.

## 3. Cross-cutting tutorials (language-agnostic, below)

The numbered examples below use `templane-ts`'s `xt` CLI to walk through scenarios that aren't about any one SDK — schema evolution, cross-language parity, Helm-chart validation. They're runnable too; just require Node.js.

---

## How to pick an example

| You want to... | Go to |
|---|---|
| Understand the absolute basics in 30 seconds | [`01-basic-hello`](01-basic-hello/) |
| See nested objects, enums, and lists | [`02-user-profile`](02-user-profile/) |
| Render realistic templates with `{% if %}` / `{% for %}` | [`03-email-with-conditionals`](03-email-with-conditionals/) |
| Catch a breaking schema change before it ships | [`04-schema-evolution`](04-schema-evolution/) |
| Verify the same template renders identically across 5 languages | [`05-cross-language-parity`](05-cross-language-parity/) |
| Apply Templane to real-world Helm chart validation | [`06-helm-chart-validation`](06-helm-chart-validation/) |

## Difficulty map

```
01 ──► 02 ──► 03 ──► 04 ──► 05 ──► 06
Basic              Medium              Advanced            Complex
```

| # | Name                         | Difficulty | Concepts shown |
|---|------------------------------|:----------:|----------------|
| 01 | basic-hello                  | ⭐         | schema, single field, render |
| 02 | user-profile                 | ⭐⭐       | nested objects, enums, lists, required vs optional |
| 03 | email-with-conditionals      | ⭐⭐⭐     | `{% if %}`, `{% for %}`, nested data |
| 04 | schema-evolution             | ⭐⭐⭐⭐   | breaking-change detector, 4 categories |
| 05 | cross-language-parity        | ⭐⭐⭐⭐   | 5 languages rendering the same template, byte-exact equality |
| 06 | helm-chart-validation        | ⭐⭐⭐⭐⭐ | real-world: Kubernetes Helm values.yaml schema |

## Common setup

All examples assume you've built the five implementations once from the repo root:

```bash
# Python (reference)
cd templane-spec/templane-core && uv sync --extra dev && cd ../..

# Python (production)
cd templane-python && uv sync --extra dev && cd ..

# TypeScript + Handlebars
cd templane-ts && npm install && npm run build && cd ..

# Java + FreeMarker
cd templane-java && ./gradlew build && cd ..

# Go
cd templane-go && go build ./... && go build -o bin/conform-adapter ./cmd/conform-adapter/ && cd ..
```

Some examples only use one language; those READMEs say so at the top.

## What you won't find here

These examples deliberately do NOT cover:

- **IDE integration** — requires LSP-server implementation (future work).
- **Template-author UX in specific IDEs** — VS Code / IntelliJ / Vim plugins don't exist yet.
- **Performance benchmarks** — Templane runs at template-load time (amortized), not render time. Benchmarks would be misleading.
- **Ecosystem adoption scenarios** — "How we rolled this out at $CORP" stories are user-contributed.

## Adding your own example

1. Create `examples/NN-your-example/`.
2. Include: `README.md` + schema/template files + sample data + runner script + expected output.
3. Follow the structure of existing examples for consistency.
4. Update this index with one row.
5. Open a PR.
