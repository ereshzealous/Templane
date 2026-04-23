# 06 — Helm Chart Validation ⭐⭐⭐⭐⭐

**Real-world scenario**: Kubernetes Helm charts are Go-template-based, and
every production outage caused by a typo in `values.yaml` is a template
bug that Templane would have caught.

This example demonstrates using Templane as the schema layer for a Helm
chart's `values.yaml`. The schema describes the *structure* the chart
expects; the type checker validates any given values file against it
before `helm install` ever touches the cluster.

> **Scope caveat.** Templane doesn't (yet) replace Helm's Go templating
> runtime. This example shows the schema + validation layer. Rendering
> is shown schematically — in production you'd wire validation as a
> pre-deploy hook or CI step and leave Helm to do the actual rendering.

## The failure mode this prevents

```
$ helm install web ./chart -f values-staging.yaml
  (success)
$ helm install web ./chart -f values-prod.yaml
  (success)
# 15 minutes later...
NAME      READY   STATUS             RESTARTS   AGE
web-abc   0/1     CrashLoopBackOff   4          10m

$ kubectl logs web-abc
  Error: unable to parse CPU request: expected string, got nil
```

The chart had a typo in the production values file. Helm rendered it
happily. The container crashed on startup. The fix was one character.
The incident burned four hours.

Templane catches this at CI time. `helm install` never runs against a
schema-invalid values file.

## Files

| File                                | Role |
|-------------------------------------|------|
| `values.schema.templane`            | Schema (deeply nested) + (simplified) Go-template body showing the rendering mental model |
| `values-prod.json`                  | Production values — passes validation |
| `values-misconfigured.json`         | Real-world-ish broken values — fails validation with 5+ errors |

## Schema highlights

Deeply nested structure matching real Helm conventions:

```yaml
resources:
  type: object
  required: true
  fields:
    requests:
      type: object
      required: true
      fields:
        cpu:
          type: string
          required: true
        memory:
          type: string
          required: true
    limits:
      type: object
      required: true
      fields:
        cpu: { type: string, required: true }
        memory: { type: string, required: true }

image:
  type: object
  required: true
  fields:
    pullPolicy:
      type: enum
      values: [Always, IfNotPresent, Never]
      required: true
```

Note the real-world patterns:
- `image.pullPolicy` is case-sensitive and enumerated — a lowercase `always` breaks.
- `resources.requests.cpu` and `.memory` are both required — forgetting one is a common outage.
- `service.type` is an enum — typos like `loadbalancer` (instead of `LoadBalancer`) cause deploy failures.

## Run — validate the good config

```bash
node templane-ts/dist/xt.js check \
  examples/06-helm-chart-validation/values.schema.templane \
  examples/06-helm-chart-validation/values-prod.json
```

Output:
```
✓ data matches schema
```

Exit code 0. Safe to pass to `helm install`.

## Run — validate the misconfigured one

```bash
node templane-ts/dist/xt.js check \
  examples/06-helm-chart-validation/values.schema.templane \
  examples/06-helm-chart-validation/values-misconfigured.json
```

Exits non-zero. Output:

```
  [type_mismatch]           Field 'replicaCount' expected number, got string
  [invalid_enum_value]      Field 'image.pullPolicy' value 'always' not in enum [Always, IfNotPresent, Never]
  [invalid_enum_value]      Field 'service.type' value 'loadbalancer' not in enum [ClusterIP, NodePort, LoadBalancer]
  [missing_required_field]  Required field 'resources.limits' is missing
  [unknown_field]           Field 'enviornment' is not defined in schema
```

Five distinct bugs, each with the exact field path. Compare to discovering
them one at a time from CrashLoopBackOff logs over four hours.

## Wiring into the Helm workflow

Two integration points, depending on your pipeline:

### Option A: Pre-commit hook

`.pre-commit-config.yaml`:

```yaml
- repo: local
  hooks:
    - id: templane-check
      name: Validate Helm values against schema
      entry: node templane-ts/dist/xt.js check
      language: system
      files: ^values-.*\.(yaml|json)$
      args: [charts/web/values.schema.templane]
```

Any commit that changes a `values-*.yaml` must pass the schema check before
the commit is created.

### Option B: CI (GitHub Actions)

```yaml
- name: Validate Helm values
  run: |
    for f in charts/web/values-*.yaml; do
      echo "Checking $f"
      node templane-ts/dist/xt.js check charts/web/values.schema.templane "$f" \
        || { echo "❌ $f fails schema"; exit 1; }
    done
```

Any PR that adds/modifies a values file that doesn't match the schema is
blocked.

## Schema evolution for Helm

The breaking-change detector from [`04-schema-evolution`](../04-schema-evolution/)
applies here too: when the chart's `values.schema.templane` changes, CI runs
the detector to tell you which downstream values files need updating.

Typical flow:
1. Chart author bumps the schema to require a new field.
2. Breaking-change detector flags it as `required_change` or a new required
   field.
3. Downstream teams' values files are checked against the new schema.
4. Everyone updates before the chart ships.

No more "we added `securityContext.runAsNonRoot: true` to the chart and
47 services broke in production because their values files didn't set it."

## What to take away

- Templane maps cleanly to real Helm chart values shapes.
- One schema file validates an arbitrary number of environment-specific
  values files (`values-dev.yaml`, `values-staging.yaml`, `values-prod.yaml`).
- CI integration is ~5 lines of shell.
- Breaking-change detection extends naturally to chart evolution — the
  schema IS the chart contract, and the detector IS the contract diff.

---

← [Back to examples index](../README.md)
