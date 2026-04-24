# 06 — Helm chart validation (sidecar adoption demo) ⭐⭐⭐⭐⭐

**This is the pitch.** Drop one schema file next to your existing Helm
chart — nothing else changes — and every `values-*.yaml` gets
type-checked in CI before `helm install` ever runs.

> **Sidecar mode** : the template body lives in its native
> format (`deployment.yaml.tmpl` here — a plain Go template), and the
> schema references it via `body: ./deployment.yaml.tmpl`. Helm tools
> see the `.tmpl` file exactly as they did before. Templane reads the
> schema and gates the data.

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

One typo in values.yaml, four hours of incident response. Templane
catches it at CI time instead.

## The files

| File                                | Role |
|-------------------------------------|------|
| `values.schema.yaml`            | Sidecar schema. References the template body + validates every `values-*.yaml`. |
| `deployment.yaml.tmpl`              | Plain Go template — the chart's deployment body. Untouched by Templane. |
| `values-prod.json`                  | Production values — passes validation |
| `values-misconfigured.json`         | Real-world-ish broken values — fails validation with 5 errors |

Notice: the schema file is **the only new file**. The `.tmpl` is just a
normal Helm chart template — you wouldn't migrate it.

## Schema highlights

```yaml
body: ./deployment.yaml.tmpl     # ← sidecar: points to the real template
engine: gotemplate

resources:
  type: object
  required: true
  fields:
    requests:
      type: object
      required: true
      fields:
        cpu:    { type: string, required: true }
        memory: { type: string, required: true }
    ...

image:
  type: object
  required: true
  fields:
    pullPolicy:
      type: enum
      values: [Always, IfNotPresent, Never]
      required: true
```

Real-world patterns this catches:
- `image.pullPolicy` — case-sensitive enum; lowercase `always` breaks before deploy
- `resources.requests.cpu` and `.memory` — both required; forgetting one is a common outage
- `service.type` — enum; typos like `loadbalancer` (should be `LoadBalancer`) fail at CI

## Run — validate the good values

```bash
node templane-ts/dist/xt.js check \
  examples/06-helm-chart-validation/values.schema.yaml \
  examples/06-helm-chart-validation/values-prod.json
```

Output:
```
✓ data matches schema
```

Exit code 0. Safe to pass to `helm install`.

## Run — validate the misconfigured values

```bash
node templane-ts/dist/xt.js check \
  examples/06-helm-chart-validation/values.schema.yaml \
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

Five distinct bugs, each with the exact field path.

## The adoption pitch

**For a team with 50 Helm charts today:**

```
charts/
  web/
    templates/
      deployment.yaml          ← existing, untouched
      service.yaml             ← existing, untouched
    values.yaml                ← existing, untouched
    values-staging.yaml        ← existing, untouched
    values-prod.yaml           ← existing, untouched
+   values.schema.yaml     ← the only new file; validates all 3 values files
```

No one rewrites `deployment.yaml`. No one moves files. The CI gate is one
line:

```yaml
- name: Validate Helm values
  run: |
    for f in charts/web/values-*.yaml; do
      xt check charts/web/values.schema.yaml "$f" \
        || { echo "❌ $f fails schema"; exit 1; }
    done
```

That's the whole adoption path. Not a migration — an addition.

## Schema evolution for Helm

The breaking-change detector from [`04-schema-evolution`](../04-schema-evolution/)
applies here too. When the chart's schema changes, CI runs the detector
to tell you which downstream values files will need updating. The schema
IS the chart contract; the detector IS the contract diff.

## What to take away

- Sidecar mode maps cleanly to real Helm-chart shape — one schema
  validates an arbitrary number of `values-*.yaml` files.
- **Zero migration cost.** The `.tmpl` is a normal Go/Helm template.
  Any Helm tooling still works on it.
- CI integration is ~5 lines of shell.
- Breaking-change detection extends naturally to chart evolution.

---

← [Back to examples index](../README.md)
