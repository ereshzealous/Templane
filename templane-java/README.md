# templane-java

**Typed template contracts for the JVM.**

Templates are silent. They take a `Map<String, Object>`, look up names by string, and render *something* — even when a field is missing, misspelled, or the wrong type. The bug ships, the customer sees a broken email, you find out four days later.

Templane fixes that at the boundary. You declare what your template expects in a tiny `.schema.yaml` next to the `.ftl`, and Templane refuses to render when the data doesn't fit — **before FreeMarker ever sees it**.

```
[ data ]  →  [ Templane: validate ]  →  [ FreeMarker: render ]  →  [ output ]
              ↑ rejects here, with a list of every problem
```

- Conformance: 40 / 40 fixtures across the [Templane spec](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- Engine binding: FreeMarker
- Add-on adapters: HTML, YAML
- Built-in: breaking-change detector for schema evolution
- Java 21 · Apache 2.0 · published on Maven Central

---

## Install

**Gradle (`build.gradle.kts`)**

```kotlin
repositories { mavenCentral() }

dependencies {
    implementation("io.github.ereshzealous:freemarker-templane:0.1.0")
}
```

**Maven (`pom.xml`)**

```xml
<dependency>
  <groupId>io.github.ereshzealous</groupId>
  <artifactId>freemarker-templane</artifactId>
  <version>0.1.0</version>
</dependency>
```

`freemarker-templane` transitively pulls `templane-core` and FreeMarker `2.3.32`. That's all you need for the FreeMarker path.

---

## Tutorial: 0 → Hero

### Step 1 · Hello, Templane

Drop two files into `src/main/resources/templates/`:

`hello.ftl`
```ftl
Hi ${user.name}! Order #${order_id} = $${amount}.
```

`hello.schema.yaml`
```yaml
body: ./hello.ftl
engine: freemarker

user:
  type: object
  required: true
  fields:
    name: { type: string, required: true }
order_id: { type: string, required: true }
amount:   { type: number, required: true }
```

Render it:

```java
import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;

import java.nio.file.Path;
import java.util.Map;

var cfg = new TemplaneConfiguration(Path.of("src/main/resources/templates"));
var tmpl = cfg.getTemplate("hello.schema.yaml");

String out = tmpl.render(Map.of(
    "user", Map.of("name", "Alice"),
    "order_id", "INV-042",
    "amount", 99.00
));
// → "Hi Alice! Order #INV-042 = $99."
```

The `.ftl` is plain FreeMarker — Templane doesn't touch the syntax. You're not migrating templates, you're adding a contract beside them.

### Step 2 · Catch bad data before it renders

Same template, broken data:

```java
import dev.templane.core.model.TypeCheckError;
import dev.templane.freemarker.TemplaneTemplateException;

var bad = new java.util.HashMap<String, Object>();
// "user" missing entirely
bad.put("order_id", 42);     // wrong type — should be string
bad.put("amount", "free");   // wrong type — should be number

try {
    tmpl.render(bad);
} catch (TemplaneTemplateException exc) {
    for (TypeCheckError e : exc.errors()) {
        System.err.printf("[%s] %s: %s%n", e.code(), e.field(), e.message());
    }
}
// [missing_required_field] user: required field 'user' is missing
// [type_mismatch]          order_id: expected string, got integer
// [type_mismatch]          amount:   expected number, got string
```

**Every** error is collected — Templane never short-circuits at the first. You see all the problems in one render attempt, not whack-a-mole-by-redeploy.

### Step 3 · Nested objects and lists

`invoice.schema.yaml`
```yaml
body: ./invoice.ftl
engine: freemarker

invoice_number: { type: string, required: true }
customer:
  type: object
  required: true
  fields:
    name:  { type: string, required: true }
    email: { type: string, required: true }
line_items:
  type: list
  required: true
  items:
    type: object
    fields:
      description: { type: string, required: true }
      qty:         { type: integer, required: true }
      unit_price:  { type: number,  required: true }
total: { type: number, required: true }
```

`invoice.ftl`
```ftl
Invoice ${invoice_number}
Bill to: ${customer.name} <${customer.email}>

<#list line_items as item>
  ${item.qty}× ${item.description} @ $${item.unit_price}
</#list>

Total: $${total}
```

Templane validates every level — nested fields, list elements, types within elements. A `qty: "two"` in line item #3 fails with `[type_mismatch] line_items[2].qty`.

### Step 4 · Breaking-change detection

When schemas evolve, downstream consumers break. Templane ships a detector you can wire into CI:

```java
import dev.templane.core.BreakingChangeDetector;
import dev.templane.core.SchemaParser;
import dev.templane.core.model.BreakingChange;
import dev.templane.core.model.TypedSchema;

import java.nio.file.*;

TypedSchema v1 = parse(Path.of("schemas/invoice.v1.yaml"));
TypedSchema v2 = parse(Path.of("schemas/invoice.v2.yaml"));

for (BreakingChange c : BreakingChangeDetector.detect(v1, v2)) {
    System.out.printf("[%s] %s: %s → %s%n",
        c.category(), c.fieldPath(), c.oldValue(), c.newValue());
}

static TypedSchema parse(Path p) throws Exception {
    var r = new SchemaParser().parse(Files.readString(p), p.getFileName().toString());
    if (r.error() != null) throw new RuntimeException(r.error());
    return r.schema();
}
```

Reports four categories — `removed_field`, `required_change`, `type_change`, `enum_value_removed`. Additive changes (new optional fields) are **not** flagged. Fail your build on any breaking change and consumers stop being surprised.

---

## Add-on adapters

The HTML and YAML adapters render Templane's intermediate representation to a target format directly — useful when you want Templane's schema validation **without** pulling in FreeMarker, or when the output format matters more than the templating engine.

### `templane-adapter-html` — auto-escaped HTML output

```kotlin
implementation("io.github.ereshzealous:templane-adapter-html:0.1.0")
```

```java
import dev.templane.html.HtmlAdapter;
// ...given a TIRResult tir from IRGenerator.generate(...)
String html = HtmlAdapter.render(tir);
// Auto-escapes &, <, >, ", '. Safe to embed user data.
```

Use when you're building HTML output and want XSS-safe rendering with schema-validated input. The output includes provenance markers (`<!-- templane template_id=... schema_id=... -->`) so downstream tooling can trace what produced it.

### `templane-adapter-yaml` — deterministic YAML output

```kotlin
implementation("io.github.ereshzealous:templane-adapter-yaml:0.1.0")
```

```java
import dev.templane.yaml.YamlAdapter;
String yaml = YamlAdapter.render(tir);
```

Use when generating config, K8s manifests, or any YAML where you need consistent output and a schema contract on the input.

> **When to reach for these:** if you're already using FreeMarker, stick with `freemarker-templane`. The HTML/YAML adapters are for non-FreeMarker pipelines or for tools that need to round-trip Templane's IR.

---

## API at a glance

### `dev.templane.freemarker` (engine binding)

```java
TemplaneConfiguration cfg = new TemplaneConfiguration(Path templateDir);
TemplaneTemplate     tmpl = cfg.getTemplate("name.schema.yaml");
String                out = tmpl.render(Map<String,Object> data);  // throws TemplaneTemplateException

// On failure:
exc.errors() // → List<TypeCheckError>, every problem at once
```

### `dev.templane.core` (protocol primitives)

```java
SchemaParser.Result   r = new SchemaParser().parse(yaml, sourceId);
List<TypeCheckError>  e = TypeChecker.check(schema, data);
TIRResult           tir = IRGenerator.generate(schema, body, data);
List<BreakingChange>  c = BreakingChangeDetector.detect(oldSchema, newSchema);
```

All models are immutable Java records: `TypedSchema`, `TemplaneField`, `TypeCheckError`, `BreakingChange`, `ASTNode`, `TIRNode`.

---

## Artifacts

| Artifact | What it is | Pull it when... |
|---|---|---|
| `freemarker-templane` | FreeMarker binding (entry point for most users) | You render `.ftl` templates |
| `templane-core` | Schema parser, type checker, IR, breaking-change detector | Pulled transitively; depend directly only for non-FreeMarker use |
| `templane-adapter-html` | TIR → escaped HTML | You need HTML output without FreeMarker |
| `templane-adapter-yaml` | TIR → YAML | You need YAML output without FreeMarker |

All under group `io.github.ereshzealous`, version `0.1.0`.

---

## Worked examples

Six runnable programs on GitHub — each is a single `main()` you can copy into your project as a starting point:

| Example | Shows |
|---|---|
| [`Hello.java`](https://github.com/ereshzealous/Templane/blob/main/templane-java/examples/src/main/java/dev/templane/examples/Hello.java) | Simplest end-to-end render |
| [`ValidationErrors.java`](https://github.com/ereshzealous/Templane/blob/main/templane-java/examples/src/main/java/dev/templane/examples/ValidationErrors.java) | Type-check error reporting |
| [`NestedAndLists.java`](https://github.com/ereshzealous/Templane/blob/main/templane-java/examples/src/main/java/dev/templane/examples/NestedAndLists.java) | Nested objects + list-of-objects validation |
| [`FreemarkerBinding.java`](https://github.com/ereshzealous/Templane/blob/main/templane-java/examples/src/main/java/dev/templane/examples/FreemarkerBinding.java) | Direct binding usage without sidecar mode |
| [`BreakingChanges.java`](https://github.com/ereshzealous/Templane/blob/main/templane-java/examples/src/main/java/dev/templane/examples/BreakingChanges.java) | Schema diff between v1 and v2 |
| [`SidecarInvoice.java`](https://github.com/ereshzealous/Templane/blob/main/templane-java/examples/src/main/java/dev/templane/examples/SidecarInvoice.java) | Real-world invoice template, sidecar mode |

---

## Requirements & compatibility

- **Java 21+** (Temurin recommended) — uses sealed interfaces and pattern-matching `switch`
- **FreeMarker 2.3.32** (transitive — override if your project pins a different version)
- Thread-safe: `TemplaneConfiguration` is reusable; `TemplaneTemplate` instances are safe to render concurrently

---

## Links

- **Spec & rationale**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- **Architecture**: [docs/ARCHITECTURE.md](https://github.com/ereshzealous/Templane/blob/main/docs/ARCHITECTURE.md)
- **Adoption guide**: [docs/ADOPTION.md](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md)
- **Issues / discussions**: [GitHub](https://github.com/ereshzealous/Templane/issues)
- **Maven Central**: [`io.github.ereshzealous`](https://central.sonatype.com/namespace/io.github.ereshzealous)

## License

Apache License 2.0.
