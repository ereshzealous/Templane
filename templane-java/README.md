# templane-java

Java 21 implementation of [Templane](../SPEC.md). Multi-module Gradle build
publishing to the **Maven local repository** (`~/.m2/repository`) for
consumption by Maven, Gradle, sbt, Ivy, or any other JVM build tool.

Ships a **FreeMarker integration** (`freemarker-templane`) and a **schema
evolution detector** (`BreakingChangeDetector`).

**Conformance:** `java 40/40` ✓ — **65 unit tests passing**.

---

## Requirements

- Java 21+ (`sealed interfaces` + `records` + pattern matching on sealed types)
- Gradle 8.5 (via the included wrapper — no system Gradle needed)

---

## Modules

```
templane-java/
├── templane-core/               ← models + SchemaParser + TypeChecker + IRGenerator + BreakingChangeDetector
├── templane-adapter-html/       ← HTML rendering
├── templane-adapter-yaml/       ← YAML rendering
├── freemarker-templane/         ← FreeMarker integration (TemplaneConfiguration + TemplaneTemplate)
└── conform-adapter/        ← fat JAR for templane-conform testing (via Shadow plugin)
```

All modules are published as Maven artifacts under group `dev.templane`, version `0.1.0`:

```
dev.templane:templane-core:0.1.0
dev.templane:templane-adapter-html:0.1.0
dev.templane:templane-adapter-yaml:0.1.0
dev.templane:freemarker-templane:0.1.0
```

---

## Build and install

```bash
cd templane-java
./gradlew build                  # compiles + runs tests
./gradlew publishToMavenLocal    # installs to ~/.m2/repository/dev/templane/
```

Verify:

```bash
ls ~/.m2/repository/dev/templane/
# freemarker-templane  templane-adapter-html  templane-adapter-yaml  templane-core
```

---

## Consume from Maven

```xml
<dependency>
    <groupId>dev.templane</groupId>
    <artifactId>freemarker-templane</artifactId>
    <version>0.1.0</version>
</dependency>
```

Ensure your `~/.m2/settings.xml` or project `repositories` section includes
`mavenLocal()`.

## Consume from Gradle

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("dev.templane:freemarker-templane:0.1.0")
}
```

## Consume from sbt

```scala
resolvers += Resolver.mavenLocal

libraryDependencies += "dev.templane" % "freemarker-templane" % "0.1.0"
```

---

## Quick start — FreeMarker integration

### Template file (`templates/greeting.templane`):

```
name:
  type: string
  required: true
items:
  type: list
  items:
    type: string
  required: true
---
Hello ${name}!
<#list items as item>- ${item}
</#list>
```

(Note: FreeMarker uses `${...}` for interpolation and `<#directive>` for
control flow, not the Jinja/Handlebars `{{ }}` syntax.)

### Java:

```java
import dev.templane.freemarker.TemplaneConfiguration;
import dev.templane.freemarker.TemplaneTemplate;
import dev.templane.freemarker.TemplaneTemplateException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        TemplaneConfiguration cfg = new TemplaneConfiguration(Path.of("./templates"));
        TemplaneTemplate tmpl = cfg.getTemplate("greeting.templane");

        try {
            String out = tmpl.render(Map.of(
                "name", "Alice",
                "items", List.of("apple", "banana")
            ));
            System.out.println(out);
        } catch (TemplaneTemplateException e) {
            // All type-check errors collected, not just the first
            e.errors().forEach(err ->
                System.err.printf("[%s] %s%n", err.code(), err.message())
            );
        }
    }
}
```

---

## Core API

```java
import dev.templane.core.SchemaParser;
import dev.templane.core.TypeChecker;
import dev.templane.core.IRGenerator;
import dev.templane.core.model.*;

SchemaParser parser = new SchemaParser();
SchemaParser.Result r = parser.parse(yamlSource, "user-profile");
TypedSchema schema = r.schema();

List<TypeCheckError> errors = TypeChecker.check(schema, Map.of(
    "name", "Alice",
    "age", 30
));

TIRResult tir = IRGenerator.generate(astNodes, data, "schema-id", "template-id");
```

---

## Schema evolution

```java
import dev.templane.core.BreakingChangeDetector;
import dev.templane.core.model.BreakingChange;

List<BreakingChange> changes = BreakingChangeDetector.detect(oldSchema, newSchema);
changes.forEach(c ->
    System.out.printf("%s  %s: %s → %s%n",
        c.category(), c.fieldPath(), c.oldValue(), c.newValue())
);
```

Four categories are reported: `removed_field`, `required_change`,
`type_change`, `enum_value_removed`. See [SPEC.md §8](../SPEC.md#8-schema-evolution).

---

## Design notes

### Sealed interfaces + records

The `TemplaneFieldType`, `ASTNode`, and `TIRNode` hierarchies use Java 21 sealed
interfaces with record variants, enabling exhaustiveness-checked pattern
matching in switch expressions:

```java
return switch (type) {
    case StringType s -> validateString(value);
    case NumberType n -> validateNumber(value);
    case EnumType e   -> validateEnum(e.values(), value);
    // ... compiler errors if any variant is missed
};
```

### Jackson polymorphism

Discriminated-union JSON is handled via `@JsonTypeInfo` + `@JsonSubTypes`
on the sealed interface, with `@JsonProperty` overrides for snake_case
JSON keys (`item_type`, `then_branch`, etc.).

### Shadow plugin for conform-adapter

`conform-adapter/` uses `com.github.johnrengelman.shadow:8.1.1` to produce a
single fat JAR at `conform-adapter/build/libs/conform-adapter-0.1.0.jar`.
This is what `templane-conform` invokes via `java -jar`.

---

## Running tests

```bash
./gradlew test
# 65 tests passing
```

Breakdown: SchemaParser (7), TypeChecker (9), IRGenerator (9),
HtmlAdapter (7), YamlAdapter (3), BreakingChangeDetector (8), freemarker-templane (6).

## Running conformance

From the repo root:

```bash
node templane-spec/templane-conform/dist/cli.js \
  --adapters "java:templane-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar"
```

Expected: `java: 40/40`.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
