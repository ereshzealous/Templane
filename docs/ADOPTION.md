# Adopting Templane

You don't migrate templates. You add schemas.

This is the document's one-line thesis. Every Templane-aware engine
binding accepts your existing `.jinja`, `.hbs`, `.ftl`, `.tmpl` files
unchanged. You drop a `.schema.yaml` next to them. The binding
follows the `body:` reference and type-checks the data before the
engine renders.

- [The mental model](#the-mental-model)
- [Jinja2 codebase (Python)](#jinja2-codebase-python)
- [Handlebars codebase (TypeScript / Node.js)](#handlebars-codebase-typescript--nodejs)
- [FreeMarker codebase (Java)](#freemarker-codebase-java)
- [Go text/template codebase](#go-texttemplate-codebase)
- [Helm chart repository](#helm-chart-repository)
- [Sharing one schema across many data files](#sharing-one-schema-across-many-data-files)
- [Wiring into CI](#wiring-into-ci)
- [Breaking-change detection on schema evolution](#breaking-change-detection-on-schema-evolution)

---

## The mental model

A **Templane schema** is a `.schema.yaml` file next to your existing
template file. It describes the shape of the data the template expects.
The schema has a `body:` key that points at the template file — the
template stays in its native format (`.jinja`, `.hbs`, `.ftl`,
`.tmpl`) and is never modified.

```
templates/
  welcome-email.jinja            ← your existing template, untouched
  welcome-email.schema.yaml      ← NEW: Templane schema beside it
```

That's the whole adoption story. Keep reading for per-engine examples.

> **Legacy note**: the repo still supports an inline-body form — schema +
> `---` + body in a single `.templane` file. New work should use
> `.schema.yaml` sidecar files. Treat inline-body support as compatibility
> behavior, not the recommended authoring format.

---

## Jinja2 codebase (Python)

**Before** — you have this:

```
templates/
  welcome-email.jinja
  password-reset.jinja
  invoice.jinja
```

You currently load them via raw `jinja2.Environment`.

**After** — one schema file per template, placed beside the `.jinja`:

```
templates/
  welcome-email.jinja                    ← untouched
  welcome-email.schema.yaml          ← NEW
  password-reset.jinja                   ← untouched
  password-reset.schema.yaml         ← NEW
  invoice.jinja                          ← untouched
  invoice.schema.yaml                ← NEW
```

The schema content:

```yaml
# templates/welcome-email.schema.yaml
body: ./welcome-email.jinja              # relative to this schema file
engine: jinja

user:
  type: object
  required: true
  fields:
    name:  { type: string, required: true }
    email: { type: string, required: true }
subscription_tier:
  type: enum
  values: [free, pro, enterprise]
  required: true
```

Your code:

```python
# Before
env = jinja2.Environment(loader=jinja2.FileSystemLoader("templates"))
tmpl = env.get_template("welcome-email.jinja")
html = tmpl.render(user={...}, subscription_tier="pro")

# After
from jinja_templane import TemplaneEnvironment, TemplaneTemplateError
env = TemplaneEnvironment("templates")
tmpl = env.get_template("welcome-email.schema.yaml")
try:
    html = tmpl.render(user={...}, subscription_tier="pro")
except TemplaneTemplateError as e:
    # e.errors is the full list of validation failures
    ...
```

Working example: [`templane-python/examples/06-sidecar/`](../templane-python/examples/06-sidecar/).

---

## Handlebars codebase (TypeScript / Node.js)

**File layout:**

```
templates/
  release-notes.hbs                      ← untouched
  release-notes.schema.yaml          ← NEW
```

**Code:**

```ts
import { compileFromPath, TemplaneHandlebarsError } from 'handlebars-templane';

const tmpl = await compileFromPath('templates/release-notes.schema.yaml');
try {
  const output = tmpl.render({ product: 'Templane', version: 'v0.1.0', ... });
} catch (err) {
  if (err instanceof TemplaneHandlebarsError) {
    for (const e of err.errors) {
      console.error(`[${e.code}] ${e.field}: ${e.message}`);
    }
  }
}
```

Working example: [`templane-ts/examples/05-sidecar/`](../templane-ts/examples/05-sidecar/).

---

## FreeMarker codebase (Java)

**File layout:**

```
src/main/resources/templates/
  invoice.ftl                            ← untouched
  invoice.schema.yaml                ← NEW
```

**Code:**

```java
import dev.templane.freemarker.*;

TemplaneConfiguration cfg = new TemplaneConfiguration(
    Path.of("src/main/resources/templates"));
TemplaneTemplate tmpl = cfg.getTemplate("invoice.schema.yaml");

try {
    String output = tmpl.render(Map.of(
        "invoice_number", "INV-2026-0042",
        "customer", Map.of(...),
        ...
    ));
} catch (TemplaneTemplateException exc) {
    for (TypeCheckError e : exc.errors()) {
        System.err.printf("[%s] %s: %s%n", e.code(), e.field(), e.message());
    }
}
```

Working example: [`templane-java/examples/src/main/resources/06sidecar/`](../templane-java/examples/src/main/resources/06sidecar/).

---

## Go text/template codebase

**File layout:**

```
templates/
  service.tmpl                           ← untouched
  service.schema.yaml                ← NEW
```

**Code:**

```go
import (
    "text/template"
    "github.com/ereshzealous/Templane/templane-go/core"
)

r := core.LoadSchemaFromPath("templates/service.schema.yaml")
if r.Error != "" { log.Fatal(r.Error) }

if errs := core.Check(r.Schema, data); len(errs) > 0 {
    for _, e := range errs {
        log.Printf("[%s] %s: %s", e.Code, e.Field, e.Message)
    }
    os.Exit(1)
}

tmpl := template.Must(template.New("svc").Parse(*r.Body))
tmpl.Execute(os.Stdout, data)
```

Working example: [`templane-go/examples/05-sidecar/`](../templane-go/examples/05-sidecar/).

---

## Helm chart repository

The adoption story at scale. A typical chart:

```
charts/web/
  Chart.yaml                             ← existing
  values.yaml                            ← existing
  values-staging.yaml                    ← existing
  values-prod.yaml                       ← existing
  templates/
    deployment.yaml                      ← existing (Go template)
    service.yaml                         ← existing (Go template)
+ values.schema.yaml                 ← ONE new file
```

The schema file references one of the chart templates as its body and
can be used to validate each values payload against it:

```yaml
# charts/web/values.schema.yaml
body: ./templates/deployment.yaml
engine: gotemplate

replicaCount:
  type: number
  required: true
image:
  type: object
  required: true
  fields:
    repository: { type: string, required: true }
    tag:        { type: string, required: true }
    pullPolicy:
      type: enum
      values: [Always, IfNotPresent, Never]
      required: true
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
    limits:
      type: object
      required: true
      fields:
        cpu:    { type: string, required: true }
        memory: { type: string, required: true }
```

In CI, with the current `xt` implementation, use JSON data files:

```yaml
- name: Validate Helm values
  run: |
    for f in charts/web/values-*.json; do
      xt check charts/web/values.schema.yaml "$f" \
        || { echo "❌ $f fails schema"; exit 1; }
    done
```

> **Current repo note**: `xt` currently reads data files as JSON objects.
> If your real-world inputs are YAML, convert them before calling `xt`, or
> validate through a language binding instead of the current CLI.

Working example: [`examples/06-helm-chart-validation/`](../examples/06-helm-chart-validation/).

**No templates were rewritten. No files were renamed. One new file.**

---

## Sharing one schema across many data files

A single schema validates an arbitrary number of data files. This is
the common shape for config/values validation:

```
envs/
  dev.values.yaml
  staging.values.yaml
  prod.values.yaml
  canary.values.yaml
envs.schema.yaml           ← ONE schema, validates all 4 files
```

```bash
for f in envs/*.values.json; do
  xt check envs.schema.yaml "$f"
done
```

You don't need a schema-per-data-file. One schema handles many.

---

## Wiring into CI

### GitHub Actions

```yaml
- name: Validate template data
  run: |
    for f in templates/*.schema.yaml; do
      # For each template, check the example data that ships with it
      base="${f%.schema.yaml}"
      if [ -f "${base}.example.json" ]; then
        xt check "$f" "${base}.example.json" \
          || { echo "❌ ${base} example failed"; exit 1; }
      fi
    done
```

### Pre-commit hook

```yaml
# .pre-commit-config.yaml
- repo: local
  hooks:
    - id: templane-check
      name: Validate values against schema
      entry: xt check charts/web/values.schema.yaml
      language: system
      files: ^charts/web/values-.*\.(yaml|json)$
```

Commits that modify a `values-*.yaml` without matching the schema are
blocked locally — before CI ever runs.

---

## Breaking-change detection on schema evolution

Adding `body:` to existing schemas doesn't change how evolution works.
When you bump a schema, the [`BreakingChangeDetector`](../SPEC.md#8-schema-evolution)
still classifies the diff across four categories:

- `removed_field` — the downstream data will break
- `required_change` — optional → required breaks existing data
- `type_change` — every downstream file needs updating
- `enum_value_removed` — downstream data using the removed value will fail

Run it across your schema versions in CI; annotate PRs when breaking
changes land; you know who needs to update their values files before
the chart/template/email ships.

---

## Summary

| You have | You add | Your templates |
|---|---|---|
| Jinja codebase | `.schema.yaml` per `.jinja` | Unchanged |
| Handlebars codebase | `.schema.yaml` per `.hbs` | Unchanged |
| FreeMarker codebase | `.schema.yaml` per `.ftl` | Unchanged |
| Go-template codebase | `.schema.yaml` per `.tmpl` | Unchanged |
| Helm chart | `values.schema.yaml` | Unchanged |

The schema describes the contract; the engine keeps rendering exactly
as before; bad data gets rejected at the boundary instead of in
production.
