# tsp-java

Java 21 implementation of [TSP](../SPEC.md). Multi-module Gradle build
publishing to the **Maven local repository** (`~/.m2/repository`) for
consumption by Maven, Gradle, sbt, Ivy, or any other JVM build tool.

Ships a **FreeMarker integration** (`freemarker-tsp`) and a **schema
evolution detector** (`BreakingChangeDetector`).

**Conformance:** `java 32/32` ✓ — **49 unit tests passing**.

---

## Requirements

- Java 21+ (`sealed interfaces` + `records` + pattern matching on sealed types)
- Gradle 8.5 (via the included wrapper — no system Gradle needed)

---

## Modules

```
tsp-java/
├── tsp-core/               ← models + SchemaParser + TypeChecker + IRGenerator + BreakingChangeDetector
├── tsp-adapter-html/       ← HTML rendering
├── tsp-adapter-yaml/       ← YAML rendering
├── freemarker-tsp/         ← FreeMarker integration (TSPConfiguration + TSPTemplate)
└── conform-adapter/        ← fat JAR for tsp-conform testing (via Shadow plugin)
```

All modules are published as Maven artifacts under group `dev.tsp`, version `0.1.0`:

```
dev.tsp:tsp-core:0.1.0
dev.tsp:tsp-adapter-html:0.1.0
dev.tsp:tsp-adapter-yaml:0.1.0
dev.tsp:freemarker-tsp:0.1.0
```

---

## Build and install

```bash
cd tsp-java
./gradlew build                  # compiles + runs tests
./gradlew publishToMavenLocal    # installs to ~/.m2/repository/dev/tsp/
```

Verify:

```bash
ls ~/.m2/repository/dev/tsp/
# freemarker-tsp  tsp-adapter-html  tsp-adapter-yaml  tsp-core
```

---

## Consume from Maven

```xml
<dependency>
    <groupId>dev.tsp</groupId>
    <artifactId>freemarker-tsp</artifactId>
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
    implementation("dev.tsp:freemarker-tsp:0.1.0")
}
```

## Consume from sbt

```scala
resolvers += Resolver.mavenLocal

libraryDependencies += "dev.tsp" % "freemarker-tsp" % "0.1.0"
```

---

## Quick start — FreeMarker integration

### Template file (`templates/greeting.tsp`):

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
import dev.tsp.freemarker.TSPConfiguration;
import dev.tsp.freemarker.TSPTemplate;
import dev.tsp.freemarker.TSPTemplateException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        TSPConfiguration cfg = new TSPConfiguration(Path.of("./templates"));
        TSPTemplate tmpl = cfg.getTemplate("greeting.tsp");

        try {
            String out = tmpl.render(Map.of(
                "name", "Alice",
                "items", List.of("apple", "banana")
            ));
            System.out.println(out);
        } catch (TSPTemplateException e) {
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
import dev.tsp.core.SchemaParser;
import dev.tsp.core.TypeChecker;
import dev.tsp.core.IRGenerator;
import dev.tsp.core.model.*;

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
import dev.tsp.core.BreakingChangeDetector;
import dev.tsp.core.model.BreakingChange;

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

The `TSPFieldType`, `ASTNode`, and `TIRNode` hierarchies use Java 21 sealed
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
This is what `tsp-conform` invokes via `java -jar`.

---

## Running tests

```bash
./gradlew test
# 49 tests passing
```

Breakdown: SchemaParser (7), TypeChecker (9), IRGenerator (9),
HtmlAdapter (7), YamlAdapter (3), BreakingChangeDetector (8), freemarker-tsp (6).

## Running conformance

From the repo root:

```bash
node tsp-spec/tsp-conform/dist/cli.js \
  --adapters "java:tsp-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar"
```

Expected: `java: 32/32`.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
