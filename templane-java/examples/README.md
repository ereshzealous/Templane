# templane-java — examples

Six runnable Java examples using the FreeMarker binding. This module
is a Gradle subproject wired up under `templane-java/examples/`.

## Setup

```bash
cd templane-java
./gradlew :examples:compileJava
```

## Run an individual example

```bash
./gradlew :examples:runExample -Pmain=dev.templane.examples.Hello
./gradlew :examples:runExample -Pmain=dev.templane.examples.ValidationErrors
./gradlew :examples:runExample -Pmain=dev.templane.examples.NestedAndLists
./gradlew :examples:runExample -Pmain=dev.templane.examples.FreemarkerBinding
./gradlew :examples:runExample -Pmain=dev.templane.examples.BreakingChanges
./gradlew :examples:runExample -Pmain=dev.templane.examples.SidecarInvoice
```

## The six examples

| # | Class | Resource dir | What it shows |
|---|-------|--------------|---------------|
| 01 | `Hello` | `01hello/` | FreeMarker render via `TemplaneConfiguration.getTemplate(...).render(...)` |
| 02 | `ValidationErrors` | `02errors/` | All 4 error codes + `did_you_mean` |
| 03 | `NestedAndLists` | `03nested/` | Nested object, enum, list-of-objects |
| 04 | `FreemarkerBinding` | `04freemarker/` | FreeMarker's `<#if>`/`<#list>` on validated data |
| 05 | `BreakingChanges` | `05breaking/` | `BreakingChangeDetector.detect(v1, v2)` |
| 06 | `SidecarInvoice` | `06sidecar/` | **Sidecar mode**: keep your `.ftl` files, add a schema beside them |

## 01 — Hello

```java
TemplaneConfiguration cfg = new TemplaneConfiguration(Path.of("src/main/resources/01hello"));
TemplaneTemplate tmpl = cfg.getTemplate("greeting.templane");
String out = tmpl.render(Map.of("name","Arya","temperature_c",22,"is_morning",true));
```

**Output:**
```
Good morning, Arya!
It's 22°C outside today.
```

## 02 — Validation errors

`render(bad)` throws `TemplaneTemplateException` whose `.errors()` is
the full list of violations.

Expected:
```
render refused: 4 error(s)

  [missing_required_field] name: Required field 'name' is missing
  [type_mismatch] age: Field 'age' expected number, got string
  [invalid_enum_value] role: Field 'role' value 'superuser' not in enum [admin, editor, viewer]
  [did_you_mean] rol: Unknown field 'rol'. Did you mean 'role'?
```

## 03 — Nested objects and lists

Realistic order receipt: nested `customer` (object + enum `tier`),
list of `items` with their own sub-schema.

## 04 — FreeMarker binding

Uses FreeMarker's `<#if>`/`<#list>`/directives on pre-validated data.
Shows the Java pattern for dispatching on enum values inside a loop.

## 05 — Breaking-change detection

Parses v1 + v2 schemas, diffs them via
`dev.templane.core.BreakingChangeDetector`, prints the changes with
category (`removed_field`, `required_change`, `type_change`,
`enum_value_removed`).

## 06 — Sidecar mode

Demonstrates SPEC 1.1 sidecar: the template body is a plain
FreeMarker file (`invoice.ftl`) that can be edited in any FreeMarker
tool. Next to it sits `invoice.schema.templane`, which declares the
data contract and references the body via `body: ./invoice.ftl`.

This is the **adoption pattern**. An existing FreeMarker codebase
doesn't migrate any templates — you drop schemas next to your `.ftl`
files, and Templane type-checks the data before FreeMarker renders it.

The example renders with good data, then trips three type errors with
bad data (missing nested field, two type mismatches) — showing that
the type check fires at the sidecar boundary exactly like embedded
mode.
