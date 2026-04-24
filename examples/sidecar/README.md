# Complex examples — default form

> **The default form** as of SPEC 1.2: a `.schema.yaml` schema file
> plus a native template body (`.jinja`, `.hbs`, `.ftl`, `.tmpl`)
> beside it, connected via the schema's `body:` key. (The term
> "sidecar" was used in earlier 1.1 docs; it's retained here only as
> the directory name.)

Every example here is **two files**: a template in its native engine
syntax (untouched, recognized by existing tooling) and a
`.schema.yaml` file beside it with `body: ./template.ext`.

These are the adoption-pattern examples — they showcase how Templane
drops into an existing codebase **without migrating any templates**.

## The examples

| # | Path | Scenario | Complexity |
|:-:|------|----------|:----------:|
| 01 | [`01-multi-env-k8s/`](01-multi-env-k8s/) | **One schema validates 4 environment values files** (dev / staging / prod / canary). Kubernetes Deployment template as the body. | ⭐⭐⭐⭐⭐ |
| 02 | [`02-order-email/`](02-order-email/) | E-commerce order confirmation email. Plain Handlebars (`.hbs`) body + nested cart / shipping / upsell schema. Renders a full email. | ⭐⭐⭐⭐ |
| 03 | [`03-feature-flags/`](03-feature-flags/) | Feature-flag service configuration. Discriminated-union-style targeting rules (attribute + operator + value + serve) with enum validation on every operator. | ⭐⭐⭐⭐ |

## Run everything

From the repo root, after building `templane-ts`:

```bash
cd templane-ts && npm install && npm run build && cd ..

# Multi-env k8s — one schema, multiple values files
for env in dev staging prod; do
  echo "=== values-$env ==="
  node templane-ts/dist/xt.js check \
    examples/sidecar/01-multi-env-k8s/values.schema.yaml \
    examples/sidecar/01-multi-env-k8s/values-$env.json
done

# Order email — check + render
node templane-ts/dist/xt.js check \
  examples/sidecar/02-order-email/order-confirmation.schema.yaml \
  examples/sidecar/02-order-email/data.json

node templane-ts/dist/xt.js render \
  examples/sidecar/02-order-email/order-confirmation.schema.yaml \
  examples/sidecar/02-order-email/data.json

# Feature flags — good + broken data
node templane-ts/dist/xt.js check \
  examples/sidecar/03-feature-flags/flags.schema.yaml \
  examples/sidecar/03-feature-flags/data.json

node templane-ts/dist/xt.js check \
  examples/sidecar/03-feature-flags/flags.schema.yaml \
  examples/sidecar/03-feature-flags/data-broken.json
# → 6 errors with exact field paths
```

## What each one demonstrates

### 01 — Multi-env Kubernetes

The flagship of the adoption pitch. The directory layout:

```
01-multi-env-k8s/
├── values.schema.yaml    ← ONE schema
├── deployment.yaml.tmpl      ← plain Go template, untouched
├── values-dev.json           ← validated against the schema
├── values-staging.json       ← validated against the same schema
├── values-prod.json          ← validated against the same schema
└── values-prod-broken.json   ← intentional bad data; 6 errors
```

**Three data files validate against one schema**. That's the key
property that makes external-body schemas useful at scale. The broken file surfaces
errors with exact field paths (`service.port`, `rollout.strategy`,
`resources.limits`, etc.), each with the enum values or expected type
called out.

### 02 — Order email (Handlebars)

Classic e-commerce transactional email, rendered end-to-end.

- `order-confirmation.hbs` — pure Handlebars; any Handlebars tool
  renders it.
- `order-confirmation.schema.yaml` — Templane schema.

The schema covers: store info, customer name/email, currency symbol,
order with line items (nested `variant` object), shipping address,
optional tracking URL, upsell section with required boolean + list of
products.

Run `xt render` to see the full email body rendered with real data.

### 03 — Feature flags configuration

Validates a feature-flag service's config before it deploys. The
schema exercises:

- **Nested lists of heterogeneous rules** — each flag has optional
  `rollout`, `targeting`, `variants` blocks
- **5 enum attributes** — `environment`, `rollout.type`,
  `targeting.attribute`, `targeting.operator`, `targeting.serve`
- **Typed weights** — `variants[].weight` as a number, not a string

The broken data file catches 6 distinct issues simultaneously —
exactly the kind of mistakes that would let a broken config reach a
production feature-flag service otherwise.

## Why this is the default

- **Zero migration**: your existing `.jinja` / `.hbs` / `.ftl` /
  `.tmpl` files don't change. You add `.schema.yaml` beside them.
- **Shared schemas**: one schema validates N data/values files.
- **Editor-friendly**: VSCode/Vim/IntelliJ all highlight the native
  template files without any plugin.
- **Git-friendly**: diffs show schema changes independently from body
  changes.

For self-contained single-template examples, see
[`../embedded/`](../embedded/).
