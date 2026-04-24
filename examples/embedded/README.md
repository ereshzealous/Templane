# Embedded-mode examples

Every example in this directory is a **single `.templane` file** that
contains schema + body separated by `---`. One file, one template,
atomic.

These are complex, realistic scenarios — not toy examples.

## The examples

| # | Path | Scenario | Complexity |
|:-:|------|----------|:----------:|
| 01 | [`01-invoice/`](01-invoice/) | E-commerce invoice with nested buyer/seller, line items with per-item tax, discount codes, currency enum, payment terms | ⭐⭐⭐⭐ |
| 02 | [`02-audit-log/`](02-audit-log/) | Compliance audit report — deeply nested events with actor/target, severity + category enums, redaction flags, attestation signature | ⭐⭐⭐⭐ |
| 03 | [`03-ci-workflow/`](03-ci-workflow/) | GitHub Actions YAML generator — jobs with needs/matrix, steps with polymorphic kinds (checkout, setup_node, run_shell, etc.), permissions, concurrency | ⭐⭐⭐⭐⭐ |

## Run everything

From the repo root, after building `templane-ts`:

```bash
cd templane-ts && npm install && npm run build && cd ..

# Check every embedded example
for d in examples/embedded/*/; do
  schema=$(ls "$d"*.templane 2>/dev/null | head -1)
  data=$(ls "$d"data-prod.json "$d"data.json 2>/dev/null | head -1)
  if [ -n "$schema" ] && [ -n "$data" ]; then
    echo "=== $d ==="
    node templane-ts/dist/xt.js check "$schema" "$data"
  fi
done

# Render example 01 (invoice)
node templane-ts/dist/xt.js render \
  examples/embedded/01-invoice/invoice.templane \
  examples/embedded/01-invoice/data-prod.json
```

## What each one demonstrates

### 01 — Invoice

- **Deep nesting**: seller → address (6 fields); buyer → billing_address
- **List of objects**: line_items, each with 8 required fields
- **Multiple enums**: currency, payment_terms
- **Optional objects**: `discount` can be absent
- **Conditional rendering**: `{{#if discount}}`, `{{#if notes}}`

Run `data-broken.json` to see 4 errors surface at once — wrong enum values, missing nested fields, type mismatches in list items.

### 02 — Audit log

- **Deep + wide nesting**: report → organization, events (list) → actor + target
- **5 enum fields**: severity, category, role, target.kind, outcome
- **Boolean-gated conditional content**: `actor.is_redacted` hides PII
- **Realistic compliance shape**: signed + timestamped attestation envelope

### 03 — CI workflow

- **Discriminated-union-style lists**: `steps[].kind` enum determines which optional fields matter
- **Optional sections at multiple levels**: `trigger.on_push`, `concurrency`, `needs`, `timeout_minutes`
- **Schema → YAML output**: template body renders valid GitHub Actions YAML

The step-kind enum (`checkout`, `setup_node`, `setup_python`, ...) is a
type-safe discriminator — Templane guards which kind-specific fields
are valid before the body renders the matching YAML directive.

## When to use embedded mode

- New template, from scratch.
- Self-contained examples (fixtures, onboarding, docs).
- One schema = one template, no reuse.
- You want one file to move, one file to version, one file to review.

For reusing one schema across many data files, see
[`../sidecar/`](../sidecar/).
