# TSP — Template Schema Protocol

**Requirements, Vision & Implementation Guide**

---

## 1. Why TSP Was Started — The Problem

Every mainstream templating engine (Jinja2, FreeMarker, Handlebars, Mustache, Liquid, etc.) shares the same fundamental flaw: **templates are untyped contracts with zero compile-time enforcement**.

### The 8 Silent Failures

When a template renders at runtime, all of the following fail silently — producing blank output, partial renders, or cryptic stack traces instead of actionable errors:

1. **Misspelled field name** — `{{ user.naem }}` instead of `{{ user.name }}` renders blank
2. **Wrong type passed** — passing a number where a list is expected causes a confusing runtime error
3. **Missing required field** — if a caller forgets to pass `title`, the template renders with a blank title and nobody notices
4. **Optional field treated as required** — template crashes when an optional field is absent
5. **Enum value typo** — `status: "actve"` instead of `"active"` passes through silently
6. **Nested path error** — `{{ order.customer.addr.city }}` fails at depth when any segment is wrong
7. **Breaking schema change** — a backend renames a field; every template using it now silently breaks
8. **No discoverability** — developers have no IDE support, hover docs, or autocomplete for template fields

### The Root Cause

The root cause is that templating engines have **no schema layer**. They accept a plain dictionary (map/object) and substitute values by string lookup. There is no type system, no validation, no tooling integration.

This is the same problem that existed for editors before the Language Server Protocol (LSP). Every editor had to build its own IDE intelligence for every language. LSP solved it by decoupling the intelligence (language server) from the editor (client). Any editor that speaks LSP gets intelligence for any language that has a server.

**TSP applies the same insight to templating:** decouple the schema layer from the templating engine. Any engine that adopts TSP gets compile-time type checking, schema validation, and IDE tooling — without replacing the engine.

---

## 2. The Vision — TSP as the "LSP for Templating"

### Core Concept

TSP is a **protocol specification**, not a library. It defines:

- A **schema language** for describing template data shapes (fields, types, optionality)
- A **typed intermediate representation (TIR)** that any engine can render
- A **type checker** that validates data against schemas at compile time
- A **conform test suite** that any TSP implementation must pass to be compliant

### The Analogy

| LSP | TSP |
|---|---|
| Language Server Protocol | Template Schema Protocol |
| Editor (VS Code, Vim, Emacs) | Templating engine (Jinja2, FreeMarker, Handlebars) |
| Language server (pylsp, tsserver) | TSP adapter (tsp-python, tsp-java) |
| TextDocumentCapabilities | Conform fixture suite |
| Any editor + any language | Any engine + any adapter |

### What "Compliant" Means

A TSP implementation is compliant if it passes all **32 fixtures** in the `tsp-conform` test suite. The fixtures cover four categories:

- **schema-parser** — Parse TSP schema YAML into a typed structure
- **type-checker** — Validate data against a schema, produce typed errors
- **ir-generator** — Walk an AST with data, produce a Typed Intermediate Representation
- **adapters** — Render a TIR to a target format (HTML, YAML)

The `tsp-conform` CLI spawns each language adapter as a subprocess, sends fixture JSON on stdin, reads pass/fail from stdout, and reports `lang N/32` across all registered adapters.

---

## 3. Architecture — Hub and Spoke

```
tsp-spec/                    ← The hub: 32 fixtures + tsp-conform CLI (Python)
├── fixtures/                ← 32 JSON fixture files (input + expected output)
├── tsp-conform              ← Test runner CLI
└── tsp-core/                ← Shared schema + AST definitions (Python reference)

tsp-ts/                      ← Spoke: TypeScript implementation
tsp-python/                  ← Spoke: Python implementation
tsp-java/                    ← Spoke: Java implementation
(future: tsp-go, tsp-rust, tsp-ruby, ...)
```

### tsp-spec is the ground truth

All implementations derive correctness from `tsp-spec/fixtures/`. If `tsp-conform` reports `lang 32/32`, the implementation is compliant. The spec is not versioned per-language; it is shared.

### Adapter Protocol

Each language adapter is a standalone executable. It speaks a simple line-delimited JSON protocol:

**Input** (stdin, one line per request):

```json
{"fixture_id": "schema-parser/basic", "fixture": { ...fixture JSON... }}
```

**Output** (stdout, one line per response):

```json
{"passed": true, "output": { ...adapter output... }}
```

or

```json
{"passed": false, "error": "Human-readable description of what went wrong"}
```

The adapter process stays alive for the duration of the `tsp-conform` run, processing one fixture at a time. This avoids per-fixture startup overhead.

---

## 4. The Type System

TSP schemas are written in YAML (the schema definition language) and describe the shape of template data.

### Schema Example

```yaml
# schema: user-profile
name:
  type: string
  required: true
age:
  type: number
  required: false
status:
  type: enum
  values: [active, inactive, pending]
  required: true
tags:
  type: list
  items:
    type: string
address:
  type: object
  fields:
    city:
      type: string
    country:
      type: string
```

### TSPFieldType — The Type Hierarchy

```
TSPFieldType
├── StringType
├── NumberType
├── BooleanType
├── NullType
├── EnumType(values: list[str])
├── ListType(item_type: TSPFieldType)
└── ObjectType(fields: dict[str, TSPField])
```

Each `TSPField` carries `name`, `type: TSPFieldType`, and `required: bool`.

### TypedSchema

A parsed schema is a `TypedSchema(id: str, fields: dict[str, TSPField])`.

### Type Errors

The type checker produces `TypeCheckError` instances:

- `missing_required_field` — required field absent from data
- `type_mismatch` — value is wrong type
- `invalid_enum_value` — value not in enum set
- `unknown_field` — field present in data but not in schema
- `did_you_mean` — Levenshtein distance ≤ 3 triggers a suggestion

---

## 5. The Template Language

TSP templates are text files with a YAML frontmatter header declaring the schema:

```
---
schema: user-profile
---
Hello {{ name }}!
{% if age %}You are {{ age }} years old.{% endif %}
{% for tag in tags %}Tag: {{ tag }}{% endfor %}
```

### AST Node Types

The template parser produces an Abstract Syntax Tree:

- `TextNode(content: str)` — literal text
- `ExprNode(field: str)` — `{{ field }}` expression
- `IfNode(condition: Condition, then_branch: list[ASTNode], else_branch: list[ASTNode])` — conditional
- `ForEachNode(var: str, iterable: str, body: list[ASTNode])` — loop

### Typed Intermediate Representation (TIR)

After type checking, the IR generator walks the AST with actual data and produces a TIR — a resolved version of the AST where all expressions have known values:

- `TIRTextNode(content: str)` — literal text unchanged
- `TIRExprNode(field: str, resolved: Any)` — expression with resolved value (can be `None`)
- `TIRIfNode(condition: bool, branch: list[TIRNode])` — evaluated condition, winning branch
- `TIRForeachNode(var: str, items: list[list[TIRNode]])` — one rendered body per item

The TIR is the format handed to adapters for rendering.

---

## 6. The Fixture Categories (32 Total)

### Category 1: schema-parser (8 fixtures)

**Input:** raw YAML schema string. **Output:** `TypedSchema` as JSON.

Key fixtures:

- `basic` — simple string/number/boolean fields
- `required-fields` — required vs optional distinction
- `enum-type` — enum with values list
- `list-type` — list with item type
- `object-type` — nested object fields
- `body-extracted` — schema YAML with template body; output must include both `schema` and `body` keys
- `invalid-schema` — malformed YAML; output must include `error` key
- `deep-nesting` — deeply nested object types

### Category 2: type-checker (8 fixtures)

**Input:** `TypedSchema` + data dict. **Output:** `{"errors": [...]}` (empty list = pass).

Key fixtures:

- `valid-data` — all fields correct, no errors
- `missing-required` — required field absent
- `type-mismatch` — string where number expected
- `invalid-enum` — value not in enum set
- `unknown-field` — extra field not in schema
- `did-you-mean` — misspelled field triggers suggestion
- `nested-object` — type errors inside nested object
- `list-type-mismatch` — list item type wrong

### Category 3: ir-generator (8 fixtures)

**Input:** `TemplateAST` + data dict + schema ID. **Output:** TIR as JSON.

Key fixtures:

- `basic-expr` — simple expression resolved from data
- `missing-path` — field not in data → `resolved = null` (never throws)
- `if-true` / `if-false` — conditional with known data
- `foreach-items` — loop over list
- `nested-path` — `user.address.city` dotted path resolution
- `condition-equals` — `==` condition evaluation
- `provenance` — TIR includes `template_id` and `schema_id`

### Category 4: adapters (8 fixtures)

**Input:** TIR. **Output:** rendered string (HTML or YAML).

Key fixtures:

- `html-basic` — render to HTML; expr values HTML-escaped
- `html-special-chars` — `<`, `>`, `&` escaped in expr values
- `html-provenance` — HTML comment header with template/schema ID
- `yaml-basic` — render to YAML; expr values NOT escaped
- `yaml-provenance` — YAML comment header
- `html-falsy-zero` — `0` renders as `"0"`, not blank
- `html-foreach` — loop rendered in HTML
- `yaml-foreach` — loop rendered in YAML

---

## 7. The Conform Adapter Protocol (Detailed)

### How tsp-conform discovers adapters

`tsp-conform` reads a config file (typically `tsp-conform.yaml` in the repo root) listing adapter commands:

```yaml
adapters:
  py:   python3 /path/to/tsp-python/conform-adapter/run.py
  java: java -jar /path/to/tsp-java/conform-adapter/target/conform-adapter-0.1.0-shaded.jar
  ts:   node /path/to/tsp-ts/conform-adapter/dist/index.js
```

### Adapter lifecycle

1. `tsp-conform` spawns each adapter as a subprocess
2. For each of 32 fixtures, sends one JSON line to the adapter's stdin
3. Reads one JSON line from stdout
4. Compares adapter output to expected fixture output
5. Reports `lang N/32` at end

### Critical implementation details

- **Line-delimited JSON:** each message is exactly one line (no pretty-printing, no extra newlines)
- **No startup/teardown messages:** adapter starts processing immediately on first stdin line
- **Fixture ID routing:** adapter uses `fixture_id` prefix to dispatch to the correct handler:
  - `fixture_id.startsWith("schema-parser")` → call schema parser
  - `fixture_id.startsWith("type-checker")` → call type checker
  - `fixture_id.startsWith("ir-generator")` → call IR generator
  - `fixture_id.startsWith("adapters/html")` → call HTML adapter
  - `fixture_id.startsWith("adapters/yaml")` → call YAML adapter

---

## 8. Implementation Plan — Four-Phase Rollout

### Phase 1: tsp-spec ✅ COMPLETE

**Goal:** Define the ground truth — 32 fixtures + `tsp-conform` CLI.

**Deliverables:**

- 32 JSON fixture files in `tsp-spec/fixtures/`
- `tsp-conform` CLI in Python
- Reference schema parser, type checker, IR generator, adapters (Python) as reference implementation inside `tsp-spec`
- `tsp-conform.yaml` config for registering adapters

**Status:** Complete. `tsp-conform` runs; spec adapter reports `spec 32/32`.

### Phase 2: tsp-ts — TypeScript Implementation ✅ COMPLETE

**Goal:** First external implementation; prove the protocol works across languages.

**Tech stack:**

- TypeScript, Node.js
- Zod for runtime validation (optional, used in type checker)
- `conform-adapter/`: Node.js script, line-delimited JSON protocol

**Architecture (mirrors spec):**

```
tsp-ts/
├── src/
│   ├── schema-parser.ts
│   ├── type-checker.ts
│   ├── ir-generator.ts
│   ├── html-adapter.ts
│   └── yaml-adapter.ts
└── conform-adapter/
    └── index.ts
```

**Status:** Complete. `ts 32/32` on `tsp-conform`.

### Phase 3: tsp-python — Python Implementation ✅ COMPLETE

**Goal:** Second external implementation; validate protocol in an interpreted language; enable Jinja2 integration.

**Tech stack:**

- Python 3.12+, `uv` for project management
- `pyyaml` for schema parsing
- `jinja2` for the `jinja_tsp` integration
- `pytest` for testing (54 tests)

**Package structure:**

```
tsp-python/
├── src/
│   ├── tsp_core/
│   │   ├── models.py              — dataclasses: TSPField, TypedSchema, ASTNode subtypes, TIRNode subtypes
│   │   ├── schema_parser.py       — parse YAML → TypedSchema
│   │   ├── type_checker.py        — validate data dict → list[TypeCheckError]
│   │   ├── ir_generator.py        — walk AST + data → TIR
│   │   ├── breaking_change.py     — detect breaking schema changes
│   │   └── hash.py                — SHA-256 content hash
│   ├── tsp_adapter_html/
│   │   └── html_adapter.py        — TIR → HTML string (html.escape on expr values)
│   ├── tsp_adapter_yaml/
│   │   └── yaml_adapter.py        — TIR → YAML string (no escaping)
│   └── jinja_tsp/
│       └── environment.py         — TSPEnvironment wrapping Jinja2; type checking at get_template() time
└── conform-adapter/
    └── run.py                     — subprocess adapter; venv sys.path bootstrap
```

**Critical detail — venv bootstrap:** `tsp-conform` spawns `python3 run.py` (system Python). The `run.py` must bootstrap the venv's site-packages:

```python
from pathlib import Path
import sys

_repo = Path(__file__).resolve().parent.parent
for _sp in (_repo / ".venv" / "lib").glob("python*/site-packages"):
    if str(_sp) not in sys.path:
        sys.path.insert(1, str(_sp))
```

**Status:** Complete. 54/54 tests passing. `py 32/32` on `tsp-conform`.

### Phase 4: tsp-java — Java Implementation ✅ COMPLETE

**Goal:** Third external implementation; validate protocol on JVM; enable FreeMarker integration.

**Tech stack:**

- Java 21 (sealed interfaces + records)
- Maven multi-module build
- Jackson 2.16 for JSON serialization/deserialization
- SnakeYAML 2.2 for schema parsing
- FreeMarker 2.3.32 for the `freemarker-tsp` integration
- JUnit 5 + AssertJ for testing
- `maven-shade-plugin` for fat JAR (`conform-adapter`)

**Maven module structure:**

```
tsp-java/                     ← parent POM (pom packaging)
├── tsp-core/                 ← model classes + schema parser + type checker + IR generator + breaking change
├── tsp-adapter-html/         ← HTML adapter
├── tsp-adapter-yaml/         ← YAML adapter
├── freemarker-tsp/           ← FreeMarker integration (TSPConfiguration, TSPTemplate, TIRResult)
└── conform-adapter/          ← fat JAR adapter (mainClass: dev.tsp.conform.ConformAdapter)
```

**Package structure (all under `dev.tsp`):**

- `dev.tsp.core.model.*` — sealed interfaces + records (`TSPFieldType`, `ASTNode`, `TIRNode`, etc.)
- `dev.tsp.core.*` — `SchemaParser`, `TypeChecker`, `IRGenerator`, `BreakingChangeDetector`
- `dev.tsp.html.*` — `HtmlAdapter`
- `dev.tsp.yaml.*` — `YamlAdapter`
- `dev.tsp.freemarker.*` — `TSPConfiguration`, `TSPTemplate`, `TIRResult`, `Format`
- `dev.tsp.conform.*` — `ConformAdapter` (main class)

**Java 21 model design (sealed interfaces + records):**

```java
// TSPFieldType — sealed interface
public sealed interface TSPFieldType
    permits StringType, NumberType, BooleanType, NullType, EnumType, ListType, ObjectType {}

public record StringType() implements TSPFieldType {}
public record EnumType(List<String> values) implements TSPFieldType {}
public record ListType(TSPFieldType itemType) implements TSPFieldType {}
public record ObjectType(Map<String, TSPField> fields) implements TSPFieldType {}

// ASTNode — sealed interface
public sealed interface ASTNode permits TextNode, ExprNode, IfNode, ForEachNode {}

public record TextNode(String content, Range range) implements ASTNode {}
public record ExprNode(String field, Range range) implements ASTNode {}
public record IfNode(Condition condition, List<ASTNode> thenBranch,
                     List<ASTNode> elseBranch, Range range) implements ASTNode {}
public record ForEachNode(String var, String iterable,
                          List<ASTNode> body, Range range) implements ASTNode {}

// TIRNode — sealed interface
public sealed interface TIRNode permits TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode {}

public record TIRTextNode(String content) implements TIRNode {}
public record TIRExprNode(String field, Object resolved) implements TIRNode {}
public record TIRIfNode(boolean condition, List<TIRNode> branch) implements TIRNode {}
public record TIRForeachNode(String var, List<List<TIRNode>> items) implements TIRNode {}
```

**Status:** Complete. 35/35 tests passing. `java 32/32` on `tsp-conform`.

---

## 9. Key Design Decisions and Invariants

### Invariant: missing path → null, never throw

In the IR generator, when a dotted path (e.g., `user.address.city`) cannot be resolved because any segment is missing, the result is `null`/`None`. The IR generator must **never** throw a `KeyError`/`NullPointerException` on missing data — that is the type checker's job (prior phase).

### Invariant: falsy values render as their string representation

`0` renders as `"0"`. `false` renders as `"false"`. Only `None`/`null` renders as `""` (empty string). The naive `value or ""` pattern silently drops falsy values and must never be used.

### Invariant: type errors are collected, not thrown

The type checker accumulates all errors and returns them as a list. It does not short-circuit on first error. Callers see the complete error picture.

### Invariant: HTML adapter escapes; YAML adapter does not

HTML adapter applies HTML entity escaping (`<`, `>`, `&`, `"`, `'`) to all resolved expression values. YAML adapter does not escape — YAML has its own quoting rules applied by the serializer, not by the adapter.

### Invariant: type checking at template load time, not render time

In `jinja_tsp` / `freemarker-tsp`, type checking is triggered when the template is loaded (e.g., `get_template()`), not when it is rendered. This enables fail-fast behavior and makes it possible to type-check templates at application startup.

### Invariant: schema-parser output includes both schema and body

For the `body-extracted` fixture, the schema parser output must include both the parsed schema object AND the raw template body string (the non-frontmatter portion of the template file). This is not "extra" output — it is required by the fixture.

### Invariant: Levenshtein distance ≤ 3 triggers did-you-mean

The type checker computes edit distance between any unknown field name and all known schema field names. If the closest match has distance ≤ 3, a `did_you_mean` suggestion is added to the error.

### Invariant: conform adapter stays alive for all 32 fixtures

The adapter subprocess must not exit after each fixture. It reads lines in a loop until stdin closes. Starting a fresh process per fixture would break the protocol.

---

## 10. Breaking Change Detection

TSP includes a breaking change detector for schema evolution:

**Breaking change categories:**

- `removed_field` — field present in old schema, absent in new schema
- `required_change` — field changed from optional to required (breaks existing callers)
- `type_change` — field type changed (any type change is breaking)
- `enum_value_removed` — an enum value removed (any caller using that value breaks)

**Non-breaking changes:**

- Adding a new optional field
- Adding a new enum value
- Making a required field optional

The detector recurses into `ObjectType` fields to detect nested breaking changes.

---

## 11. Replication Checklist

To replicate this project on a personal laptop:

### Prerequisites

- Python 3.12+ (`python3 --version`)
- Node.js 20+ (`node --version`)
- Java 21+ (`java --version`)
- Maven 3.9+ (`mvn --version`)
- `uv` (Python package manager):

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

### Step 1: Clone the repositories

```bash
git clone <tsp-spec-repo>   tsp-spec
git clone <tsp-ts-repo>     tsp-ts
git clone <tsp-python-repo> tsp-python
git clone <tsp-java-repo>   tsp-java
```

### Step 2: Set up tsp-python

```bash
cd tsp-python
uv sync
source .venv/bin/activate
pytest                      # expect 54/54 passing
```

### Step 3: Set up tsp-java

```bash
cd tsp-java
mvn package -q              # builds fat JAR in conform-adapter/target/
```

### Step 4: Register adapters in tsp-conform.yaml

Edit `tsp-spec/tsp-conform.yaml` to add adapter paths pointing to your local clones.

### Step 5: Run tsp-conform

The `tsp-conform` CLI is a Node.js tool at `tsp-spec/tsp-conform/dist/cli.js`.

**Critical:** The Java adapter JAR targets Java 21 bytecode. If your system `java` is older than 21, prepend the JDK 21+ bin directory to `PATH`:

```bash
# macOS with Homebrew (Java 21 installed via brew):
export PATH="$(brew --prefix openjdk@21)/bin:$PATH"

# Verify:
java -version              # must show 21+
```

Run `tsp-conform` for all adapters:

```bash
node tsp-spec/tsp-conform/dist/cli.js \
  --adapters \
    "py:tsp-python/conform-adapter/run.py" \
    "java:tsp-java/conform-adapter/target/conform-adapter-0.1.0.jar" \
    "ts:tsp-ts/conform-adapter/dist/index.js"

# Expected: py 32/32, java 32/32, ts 32/32
```

---

## 12. Project Status Summary (as of 2026-04-23)

| Component | Status | Tests |
|---|---|---|
| `tsp-spec` (fixtures + tsp-conform) | ✅ Complete | 32 fixtures |
| `tsp-ts` | ✅ Complete | ts 32/32 |
| `tsp-python` | ✅ Complete | 54 tests, py 32/32 |
| `tsp-java` | ✅ Complete | 35 tests, java 32/32 |

---

## 13. Personal Laptop Setup and GitHub Publishing

This section is a complete, self-contained guide to replicate the full TSP project on a personal macOS laptop and publish it to GitHub under the Apache 2.0 license.

### 13.1 Prerequisites — Install Everything First

Open Terminal and run these in order. Each command includes a verification check.

**Homebrew (package manager):**

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
brew --version                        # should print Homebrew 4.x.x
```

**Git:**

```bash
brew install git
git --version                         # should print git version 2.x.x
git config --global user.name  "Your Name"
git config --global user.email "your@email.com"
```

**Python 3.12+:**

```bash
brew install python@3.12
python3.12 --version                  # Python 3.12.x
```

**uv (Python project manager — replaces pip/venv/pyproject):**

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
source ~/.zshrc                       # reload shell
uv --version                          # uv 0.x.x
```

**Node.js 20+ and npm:**

```bash
brew install node@20
node --version                        # v20.x.x
npm  --version                        # 10.x.x
```

**Java 21+ (JDK via Homebrew):**

```bash
brew install openjdk
java -version                         # openjdk version "21" or higher
```

If `java -version` still shows an old version after install, add it to `PATH`:

```bash
echo 'export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version                         # must show 21+
```

**Maven 3.9+:**

```bash
brew install maven
mvn --version                         # Apache Maven 3.9.x, Java version: 21 (critical)
```

If Maven shows wrong Java version, set `JAVA_HOME`:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk
mvn --version                         # now shows Java 21
```

Add permanently:

```bash
echo 'export JAVA_HOME=/opt/homebrew/opt/openjdk' >> ~/.zshrc
source ~/.zshrc
```

### 13.2 GitHub Setup

**Create a GitHub account:**

Go to https://github.com and sign up if you don't have a personal account.

**Generate an SSH key (one-time setup):**

```bash
ssh-keygen -t ed25519 -C "your@email.com"
# Press Enter to accept default path (~/.ssh/id_ed25519)
# Set a passphrase or leave blank

# Copy the public key to clipboard:
cat ~/.ssh/id_ed25519.pub | pbcopy
```

Go to GitHub → Settings → SSH and GPG keys → New SSH key → paste → Save.

Test it:

```bash
ssh -T git@github.com
# Should print: Hi yourusername! You've successfully authenticated...
```

**Create four public repositories on GitHub:**

Go to https://github.com/new and create each of these (one at a time):

| Repo name | Description |
|---|---|
| `tsp-spec` | TSP specification: 32 conformance fixtures + tsp-conform CLI |
| `tsp-ts` | TSP TypeScript implementation (@tsp/core, xt CLI, handlebars-tsp) |
| `tsp-python` | TSP Python implementation (tsp_core, jinja_tsp, adapters) |
| `tsp-java` | TSP Java implementation (tsp-core, freemarker-tsp, adapters) |

**Settings for each:**

- Visibility: **Public**
- Initialize with README: **No** (you'll push existing code)
- Add `.gitignore`: **No** (already in your repos)
- License: **No** (you'll add Apache 2.0 manually)

### 13.3 Apache 2.0 License

Apache 2.0 is the right license for an open-source developer framework. It lets anyone use, modify, and distribute TSP — including in commercial products — while protecting you from liability and preserving attribution.

**The LICENSE file content:**

Create a file named `LICENSE` (no extension) in the root of each repo with the full Apache 2.0 text. Download it from:

```
https://www.apache.org/licenses/LICENSE-2.0.txt
```

At the bottom of the file, in the APPENDIX section, replace `[yyyy]` with `2025` and `[name of copyright owner]` with your name.

**The short header to add to every source file:**

For **TypeScript/Java** (`.ts`, `.java`):

```
// Copyright 2025 <Your Name>
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
```

For **Python** (`.py`):

```
# Copyright 2025 <Your Name>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
```

### 13.4 Copy Code from Work Laptop to Personal Laptop

There are two approaches. Use whichever fits your situation.

#### Option A — Direct copy via USB / AirDrop / shared drive (simplest)

On your work laptop, create an archive of each repo:

```bash
cd ~/Documents/Dev/Work/IMP

# Archive each repo (excludes node_modules, .venv, target/, .git/)
tar --exclude='.git' --exclude='node_modules' --exclude='.venv' \
    --exclude='target' --exclude='__pycache__' --exclude='*.pyc' \
    -czf ~/Desktop/tsp-spec.tar.gz tsp-spec

tar --exclude='.git' --exclude='node_modules' --exclude='.venv' \
    --exclude='target' --exclude='__pycache__' --exclude='*.pyc' \
    -czf ~/Desktop/tsp-ts.tar.gz tsp

tar --exclude='.git' --exclude='node_modules' --exclude='.venv' \
    --exclude='target' --exclude='__pycache__' --exclude='*.pyc' \
    -czf ~/Desktop/tsp-python.tar.gz tsp-python

tar --exclude='.git' --exclude='node_modules' --exclude='.venv' \
    --exclude='target' --exclude='__pycache__' --exclude='*.pyc' \
    -czf ~/Desktop/tsp-java.tar.gz tsp-java
```

Transfer the four `.tar.gz` files to your personal laptop, then extract:

```bash
mkdir -p ~/Projects/tsp
cd ~/Projects/tsp
tar -xzf ~/Desktop/tsp-spec.tar.gz
tar -xzf ~/Desktop/tsp-ts.tar.gz        # extracts as 'tsp' folder
tar -xzf ~/Desktop/tsp-python.tar.gz
tar -xzf ~/Desktop/tsp-java.tar.gz

# Rename tsp → tsp-ts to match GitHub repo name
mv tsp tsp-ts
```

#### Option B — Push from work laptop → GitHub → pull on personal laptop

On your work laptop, add GitHub as a remote for each repo and push:

```bash
cd ~/Documents/Dev/Work/IMP/tsp-spec
git remote add origin git@github.com:YOURUSERNAME/tsp-spec.git
git push -u origin main

cd ~/Documents/Dev/Work/IMP/tsp
git remote add origin git@github.com:YOURUSERNAME/tsp-ts.git
git push -u origin main

cd ~/Documents/Dev/Work/IMP/tsp-python
git remote add origin git@github.com:YOURUSERNAME/tsp-python.git
git push -u origin main

cd ~/Documents/Dev/Work/IMP/tsp-java
git remote add origin git@github.com:YOURUSERNAME/tsp-java.git
git push -u origin main
```

Then on your personal laptop:

```bash
mkdir -p ~/Projects/tsp && cd ~/Projects/tsp
git clone git@github.com:YOURUSERNAME/tsp-spec.git
git clone git@github.com:YOURUSERNAME/tsp-ts.git
git clone git@github.com:YOURUSERNAME/tsp-python.git
git clone git@github.com:YOURUSERNAME/tsp-java.git
```

### 13.5 Add LICENSE and Copyright Headers

After the code is on your personal laptop (regardless of which option above), add Apache 2.0 to each repo.

For each of the four repos:

```bash
cd ~/Projects/tsp/tsp-spec         # (repeat for tsp-ts, tsp-python, tsp-java)
```

Create `LICENSE` file using the full text from section 13.3 above (fill in year and your name). Add the appropriate copyright header to every source file per-language.

After adding LICENSE files, commit and push each repo:

```bash
git add LICENSE
git commit -m "chore: add Apache 2.0 license"
git push
```

### 13.6 README Structure for Each Repo

Each repo needs a `README.md`. Use these templates.

#### tsp-spec/README.md

````markdown
# tsp-spec

The TSP (Template Schema Protocol) specification — conformance fixtures and test runner.

## What Is TSP?

TSP adds compile-time schema safety to any templating engine (Jinja2, FreeMarker, Handlebars, etc.).
It is a protocol: any implementation that passes the 32 conformance fixtures is TSP-compliant.

## Repository Contents

- `fixtures/` — 32 YAML conformance fixtures (schema-parser, typechecker, ir-generator, breaking-change)
- `tsp-conform/` — CLI test runner (Node.js)
- `SPEC.md` — Full TSP 1.0 specification

## Running the Conformance Suite

```bash
cd tsp-conform && npm install && npm run build
node dist/cli.js --adapters "py:/path/to/tsp-python/conform-adapter/run.py"
```

## License

Apache 2.0 — see `LICENSE`.
````

#### tsp-ts/README.md

````markdown
# tsp-ts

TypeScript implementation of the TSP (Template Schema Protocol).

## Packages

| Package | Description |
|---------|-------------|
| `@tsp/core` | Schema parser, type checker, IR generator |
| `xt-adapter-html` | HTML rendering adapter |
| `xt-adapter-yaml` | YAML rendering adapter |
| `handlebars-tsp` | Handlebars integration |
| `xt` | CLI: render, test, check, dev, build |

## Quick Start

```bash
npm install @tsp/core
```

```ts
import { SchemaParser, TypeChecker } from '@tsp/core'

const schema = new SchemaParser().parse('invoice', 'name: string\ntotal: number')
const errors = new TypeChecker().check(schema, { name: 'Acme', total: 1500 })
console.log(errors) // []
```

## Conformance

`ts 32/32` — passes all TSP conformance fixtures.

## License

Apache 2.0 — see `LICENSE`.
````

#### tsp-python/README.md

````markdown
# tsp-python

Python implementation of the TSP (Template Schema Protocol).

## Packages

| Package | Description |
|---------|-------------|
| `tsp-core` | Schema parser, type checker, IR generator |
| `tsp-adapter-html` | HTML rendering adapter |
| `tsp-adapter-yaml` | YAML rendering adapter |
| `jinja-tsp` | Jinja2 integration |

## Quick Start

```bash
pip install tsp-core jinja-tsp
```

```python
from tsp_core import parse_schema_frontmatter
from tsp_adapter_html import html_adapter

schema, body = parse_schema_frontmatter("name: string\ntotal: number\n---\nHello {{ name }}")
```

## Conformance

`py 32/32` — passes all TSP conformance fixtures.

## License

Apache 2.0 — see `LICENSE`.
````

#### tsp-java/README.md

````markdown
# tsp-java

Java implementation of the TSP (Template Schema Protocol).

## Modules

| Module | Description |
|--------|-------------|
| `tsp-core` | Schema parser, type checker, IR generator |
| `tsp-adapter-html` | HTML rendering adapter |
| `tsp-adapter-yaml` | YAML rendering adapter |
| `freemarker-tsp` | FreeMarker integration |

## Requirements

- Java 21+
- Maven 3.9+

## Quick Start

```xml
<dependency>
  <groupId>dev.tsp</groupId>
  <artifactId>tsp-core</artifactId>
  <version>0.1.0</version>
</dependency>
```

```java
var parser = new dev.tsp.core.SchemaParser();
var schema = parser.parse("invoice", "name: string\ntotal: number");
var errors = new dev.tsp.core.TypeChecker()
    .check(schema, Map.of("name", "Acme", "total", 1500.0));
```

## Conformance

`java 32/32` — passes all TSP conformance fixtures.

## License

Apache 2.0 — see `LICENSE`.
````

### 13.7 Build and Verify on Personal Laptop

After everything is set up, do a full end-to-end verify.

**Step 1: Build tsp-ts**

```bash
cd ~/Projects/tsp/tsp-ts
npm install                           # install all workspace deps
npm run build                         # compile all packages
cd conform-adapter && npm run build
```

Expected: no errors, `conform-adapter/dist/index.js` exists.

**Step 2: Build tsp-python**

```bash
cd ~/Projects/tsp/tsp-python
uv sync                               # creates .venv, installs all packages
source .venv/bin/activate
pytest                                # expect 54/54 passing
```

**Step 3: Build tsp-java**

```bash
cd ~/Projects/tsp/tsp-java
export JAVA_HOME=/opt/homebrew/opt/openjdk  # critical: ensure Java 21
mvn package -q                              # builds all modules including fat JAR
ls conform-adapter/target/conform-adapter-0.1.0.jar  # must exist
```

Expected: BUILD SUCCESS. JAR at `conform-adapter/target/conform-adapter-0.1.0.jar`.

**Step 4: Build tsp-conform**

```bash
cd ~/Projects/tsp/tsp-spec/tsp-conform
npm install
npm run build
ls dist/cli.js                        # must exist
```

**Step 5: Run all adapters — expect 32/32 for each**

```bash
export PATH="/opt/homebrew/opt/openjdk/bin:$PATH"   # Java 21 in PATH

node ~/Projects/tsp/tsp-spec/tsp-conform/dist/cli.js \
  --adapters \
    "py:$HOME/Projects/tsp/tsp-python/conform-adapter/run.py" \
    "java:$HOME/Projects/tsp/tsp-java/conform-adapter/target/conform-adapter-0.1.0.jar" \
    "ts:$HOME/Projects/tsp/tsp-ts/conform-adapter/dist/index.js"
```

Expected output:

```
Running 32 fixtures across 3 implementation(s)...
  ✓ py:   32/32
  ✓ java: 32/32
  ✓ ts:   32/32

All implementations conformant.
```

### 13.8 Push Everything to GitHub (Final Step)

If you used Option A (direct copy) in section 13.4, you now need to initialize git and push each repo:

```bash
cd ~/Projects/tsp/tsp-spec
git init
git add .
git commit -m "feat: initial TSP specification — 32 fixtures + tsp-conform CLI"
git branch -M main
git remote add origin git@github.com:YOURUSERNAME/tsp-spec.git
git push -u origin main
```

Repeat for the other three repos with appropriate commit messages:

- `tsp-ts`: `"feat: TSP TypeScript implementation — ts 32/32 conformant"`
- `tsp-python`: `"feat: TSP Python implementation — py 32/32 conformant"`
- `tsp-java`: `"feat: TSP Java implementation — java 32/32 conformant"`

If you used Option B (push from work laptop), the code is already on GitHub. Just add the LICENSE file and commit as noted in section 13.5.

### 13.9 Checklist — Complete When All Green

**Prerequisites:**

- [ ] Homebrew installed
- [ ] Git configured (name + email)
- [ ] Python 3.12+ available
- [ ] `uv` installed
- [ ] Node.js 20+ available
- [ ] Java 21+ as default `java` (verify: `java -version` shows 21+)
- [ ] Maven uses Java 21+ (verify: `mvn --version` shows Java 21+)
- [ ] `JAVA_HOME` set to `/opt/homebrew/opt/openjdk`

**GitHub:**

- [ ] SSH key added to GitHub
- [ ] 4 public repos created: `tsp-spec`, `tsp-ts`, `tsp-python`, `tsp-java`

**Code:**

- [ ] All 4 repos on personal laptop under `~/Projects/tsp/`
- [ ] `LICENSE` file in each repo (Apache 2.0, your name, year 2025)
- [ ] `README.md` in each repo

**Build verification:**

- [ ] `tsp-ts`: `npm run build` succeeds
- [ ] `tsp-python`: 54/54 pytest passing
- [ ] `tsp-java`: `mvn package` succeeds, JAR exists
- [ ] `tsp-conform`: `npm run build` succeeds

**End-to-end:**

- [ ] `py 32/32` ✓
- [ ] `java 32/32` ✓
- [ ] `ts 32/32` ✓

**Published:**

- [ ] All 4 repos pushed and visible on GitHub
- [ ] Each repo has `LICENSE`, `README`, and source code

### 13.10 Optional: Add GitHub Topics and Description

Once repos are on GitHub, go to each repo page and:

1. Click the gear icon next to "About"
2. Add a description (e.g., *"TSP Python implementation — compile-time schema safety for Jinja2 templates"*)
3. Add topics: `tsp`, `template`, `schema`, `jinja2` (or `freemarker`, `handlebars`, `typescript` as appropriate), `type-checking`, `templating`

This makes the repos discoverable.

---

*Document updated 2026-04-23 — added Section 13: Personal Laptop Setup and GitHub Publishing.*
