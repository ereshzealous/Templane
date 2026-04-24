# templane-java

**Java 21 implementation of [Templane](https://github.com/ereshzealous/Templane)** — typed template contracts for FreeMarker and other JVM engines.

Templane adds compile-time schema validation to your templates. Define what data your template expects in a small `.schema.yaml` file next to your `.ftl`, and Templane catches missing fields, typos, and wrong types **before FreeMarker renders**, not at 2am in production.

- **Conformance:** 40/40 fixtures across the [Templane protocol](https://github.com/ereshzealous/Templane/blob/main/SPEC.md) · 65 unit tests
- **Engine binding:** FreeMarker (`freemarker-templane`)
- **Also ships:** breaking-change detector
- **Runtime:** Java 21 (Temurin recommended)
- **License:** Apache 2.0

---

## Install

### Gradle (`build.gradle.kts`)

```kotlin
dependencies {
    implementation("dev.templane:templane-core:0.1.0")
    implementation("dev.templane:freemarker-templane:0.1.0")
}
```

### Maven (`pom.xml`)

```xml
<dependency>
  <groupId>dev.templane</groupId>
  <artifactId>templane-core</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>dev.templane</groupId>
  <artifactId>freemarker-templane</artifactId>
  <version>0.1.0</version>
</dependency>
```

---

## Quick start

Create `src/main/resources/templates/email.ftl` (plain FreeMarker — not modified by Templane):

```ftl
Hi ${user.name}! Your order #${order_id} total is $${amount}.
```

Create `src/main/resources/templates/email.schema.yaml` next to it:

```yaml
body: ./email.ftl
engine: freemarker

user:
  type: object
  required: true
  fields:
    name: { type: string, required: true }
order_id:
  type: string
  required: true
amount:
  type: number
  required: true
```

Use from Java:

```java
import dev.templane.freemarker.*;
import dev.templane.core.model.TypeCheckError;

import java.nio.file.Path;
import java.util.Map;

TemplaneConfiguration cfg = new TemplaneConfiguration(
    Path.of("src/main/resources/templates"));
TemplaneTemplate tmpl = cfg.getTemplate("email.schema.yaml");

try {
    String output = tmpl.render(Map.of(
        "user", Map.of("name", "Alice"),
        "order_id", "INV-042",
        "amount", 99.00
    ));
    System.out.println(output);
    // → "Hi Alice! Your order #INV-042 total is $99."
} catch (TemplaneTemplateException exc) {
    for (TypeCheckError e : exc.errors()) {
        System.err.printf("[%s] %s: %s%n", e.code(), e.field(), e.message());
    }
}
```

---

## Validation errors — caught before rendering

```java
// Missing field + wrong types all trip at once
Map<String, Object> bad = new HashMap<>();
// "user" missing entirely
bad.put("order_id", 42);      // wrong type
bad.put("amount", "free");    // wrong type

try {
    tmpl.render(bad);
} catch (TemplaneTemplateException exc) {
    exc.errors().forEach(e ->
        System.err.printf("[%s] %s%n", e.code(), e.field()));
}
// [missing_required_field] user
// [type_mismatch] order_id
// [type_mismatch] amount
```

All errors are collected — never short-circuits at the first.

---

## Breaking-change detection

Detect schema evolution issues before they break downstream data:

```java
import dev.templane.core.BreakingChangeDetector;
import dev.templane.core.SchemaParser;
import dev.templane.core.model.BreakingChange;
import dev.templane.core.model.TypedSchema;

import java.nio.file.*;
import java.util.List;

TypedSchema oldSchema = loadSchema(Path.of("schemas/v1.yaml"));
TypedSchema newSchema = loadSchema(Path.of("schemas/v2.yaml"));

List<BreakingChange> changes = BreakingChangeDetector.detect(oldSchema, newSchema);
for (BreakingChange c : changes) {
    System.out.printf("[%s] %s: %s → %s%n",
        c.category(), c.fieldPath(), c.oldValue(), c.newValue());
}

static TypedSchema loadSchema(Path p) throws Exception {
    SchemaParser.Result r = new SchemaParser().parse(
        Files.readString(p), p.getFileName().toString());
    if (r.error() != null) throw new RuntimeException(r.error());
    return r.schema();
}
```

Four categories: `removed_field`, `required_change`, `type_change`, `enum_value_removed`. Safe additions are NOT reported.

---

## API

### `dev.templane.freemarker` — the engine binding

```java
public class TemplaneConfiguration {
    public TemplaneConfiguration(Path templateDir);
    public TemplaneTemplate getTemplate(String name);
}

public class TemplaneTemplate {
    public String name();
    public TypedSchema schema();
    public String render(Map<String, Object> data);  // throws TemplaneTemplateException
}

public class TemplaneTemplateException extends RuntimeException {
    public List<TypeCheckError> errors();
}
```

### `dev.templane.core` — the protocol primitives

```java
public class SchemaParser {
    public Result parse(String yaml, String id);
    public static Result loadFromPath(Path schemaPath);  // follows body: reference
}
public class TypeChecker { static List<TypeCheckError> check(TypedSchema, Map); }
public class IRGenerator { static TIRResult generate(...); }
public class BreakingChangeDetector { static List<BreakingChange> detect(TypedSchema, TypedSchema); }
```

All models are immutable records: `TypedSchema`, `TemplaneField`, `TypeCheckError`, `BreakingChange`, `ASTNode`, `TIRNode`.

---

## Why Templane

Templates are untyped contracts. They accept a bag of values, look up names by string, and render *something* — even when the data has a typo, a missing field, or a wrong type. The failure is silent: the render succeeds, the customer gets a broken email, and you find out four days later.

Templane fixes this at the boundary. A schema next to your template declares what the template expects; the binding refuses to render when the data doesn't match. See the [main README](https://github.com/ereshzealous/Templane) for the full pitch.

---

## Adoption pattern

**You don't migrate templates.** Your existing `.ftl` files stay as-is. You drop one `.schema.yaml` beside each one:

```
src/main/resources/templates/
  welcome.ftl                 ← untouched
  welcome.schema.yaml         ← NEW
  invoice.ftl                 ← untouched
  invoice.schema.yaml         ← NEW
```

Your code switches from `freemarker.Configuration` to `TemplaneConfiguration`. That's the migration.

See the [ADOPTION guide](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md) for per-engine walkthroughs.

---

## Gradle module layout

The Maven artifacts map to independent Gradle subprojects:

| Artifact | Purpose |
|---|---|
| `dev.templane:templane-core` | Schema parser, type checker, IR generator, breaking-change detector |
| `dev.templane:templane-adapter-html` | HTML adapter (entity escaping + provenance markers) |
| `dev.templane:templane-adapter-yaml` | YAML adapter |
| `dev.templane:freemarker-templane` | FreeMarker binding |

Depend on `freemarker-templane` for the user-facing API; transitively brings `templane-core`.

---

## Examples

Six worked examples under the repo's [`templane-java/examples/`](https://github.com/ereshzealous/Templane/tree/main/templane-java/examples): hello, validation errors, nested objects and lists, FreeMarker features, breaking-change detection, and a full sidecar-mode invoice renderer.

---

## Building from source

```bash
git clone https://github.com/ereshzealous/Templane.git
cd Templane/templane-java
./gradlew build        # tests + all JARs
./gradlew test         # 65 tests
```

**Note**: use `./gradlew` (bundled Gradle 8.5). Do NOT use system `gradle` 9+ — the Shadow plugin used by `conform-adapter` is incompatible.

---

## Links

- **Repo**: https://github.com/ereshzealous/Templane
- **Full spec (RFC 2119)**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- **Architecture**: [docs/ARCHITECTURE.md](https://github.com/ereshzealous/Templane/blob/main/docs/ARCHITECTURE.md) — 12 Mermaid diagrams
- **Adoption guide**: [docs/ADOPTION.md](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md)
- **Issues**: [GitHub Issues](https://github.com/ereshzealous/Templane/issues)

## License

Apache License 2.0
