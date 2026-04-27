# Templane Examples

Three flavors of examples, grouped by purpose:

## 1. By form — complex, realistic scenarios

These dirs group complex, production-shaped examples. The default
form — `.schema.yaml` with `body:` pointing at a native template
file — is under [`sidecar/`](sidecar/). The legacy inline-body form
is under [`embedded/`](embedded/); kept for reference only.

| Form | Dir | What you see there |
|---|---|---|
| **Default** — `.schema.yaml` + native body file | [`sidecar/`](sidecar/) — 3 examples | Multi-environment Kubernetes deployment validation, order confirmation email, feature-flag config validation |
| **Legacy inline** — one `.templane` file with `---` separator | [`embedded/`](embedded/) — 3 examples | Invoice with taxes and discounts, compliance audit log report, GitHub Actions workflow generator. Kept for reference; not recommended for new work. |

## 2. Per-language examples (in each SDK's idioms)

If you want to see how Templane feels in a specific language, start here:

| Language | Examples dir |
|---|---|
| Python (with Jinja2) | [`templane-python/examples/`](../templane-python/examples/) — 6 examples |
| TypeScript (with Handlebars) | [`templane-ts/examples/`](../templane-ts/examples/) — 5 examples |
| Java 21 (with FreeMarker) | [`templane-java/examples/`](../templane-java/examples/) — 6 examples |
| Go (with `text/template`) | [`templane-go/examples/`](../templane-go/examples/) — 5 examples |

Each dir has its own `README.md` with setup, run commands, and expected output. Examples cover: hello-world → validation errors → nested/lists → engine binding → breaking-change detection → external-body schema.

## 3. Cross-cutting tutorials (language-agnostic, below)

The numbered examples below use `templane-ts`'s `xt` CLI to walk through scenarios that aren't about any one SDK — schema evolution, cross-language parity, Helm-chart validation. They're runnable too; just require Node.js.

---

## The numbered examples

| # | Dir | Concepts |
|---|---|---|
| 01 | [`01-basic-hello`](01-basic-hello/) | schema, single field, render |
| 02 | [`02-user-profile`](02-user-profile/) | nested objects, enums, lists, required vs optional |
| 03 | [`03-email-with-conditionals`](03-email-with-conditionals/) | `{% if %}`, `{% for %}`, nested data |
| 04 | [`04-schema-evolution`](04-schema-evolution/) | breaking-change detector, 4 categories |
| 05 | [`05-cross-language-parity`](05-cross-language-parity/) | one template renders byte-identically across 5 languages |
| 06 | [`06-helm-chart-validation`](06-helm-chart-validation/) | real-world: Kubernetes Helm `values.yaml` schema |

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

## Adding an example

Create `examples/NN-your-example/` with a `README.md`, schema/template files, sample data, a runner, and expected output. Add a row above and open a PR.
