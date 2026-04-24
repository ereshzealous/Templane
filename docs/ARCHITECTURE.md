# Architecture

This document explains how Templane is put together: the protocol pipeline,
how the five implementations stay in lock-step, how engines plug in, and
how schema evolution is checked. Every diagram is Mermaid and renders
natively on GitHub.

- [1. Where Templane fits (context)](#1-where-templane-fits-context)
- [2. The rendering pipeline](#2-the-rendering-pipeline)
- [3. Per-language anatomy](#3-per-language-anatomy)
- [4. Cross-implementation conformance](#4-cross-implementation-conformance)
- [5. The IR (intermediate representation)](#5-the-ir-intermediate-representation)
- [6. Engine-binding pattern](#6-engine-binding-pattern)
- [7. xt CLI pipeline](#7-xt-cli-pipeline)
- [8. Schema evolution & breaking-change detection](#8-schema-evolution--breaking-change-detection)
- [9. Publishing topology](#9-publishing-topology)

---

## 1. Where Templane fits (context)

Most template engines take raw data and raw strings and produce output.
Templane inserts a **typed schema contract** between the data and the
engine, and a **checker + IR generator** before rendering.

```mermaid
flowchart LR
    subgraph Before["Without Templane"]
        direction LR
        D1[Data<br/>JSON/YAML] --> E1[Template Engine<br/>Jinja · Handlebars ·<br/>FreeMarker · Go tmpl]
        E1 --> O1[Output<br/>HTML/YAML/Text]
        E1 -.silent failure.-> BUG[(Typo in field<br/>renders blank.<br/>Bug ships.)]
    end

    subgraph With["With Templane"]
        direction LR
        SCHEMA[.templane<br/>schema + body] --> CHECK{Type<br/>checker}
        D2[Data<br/>JSON/YAML] --> CHECK
        CHECK -->|fails| ERR[/Rich errors<br/>with field paths/]
        CHECK -->|ok| IR[IR<br/>resolved AST]
        IR --> ADAPT[Adapter<br/>HTML / YAML / engine]
        ADAPT --> O2[Output]
    end

    style BUG fill:#C75B3C,color:#F4EDE0,stroke:#2B2A28
    style ERR fill:#D9A441,color:#2B2A28,stroke:#2B2A28
    style O2 fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
```

**The shift**: rendering moves from *"pray the data matches"* to *"prove
the data matches, then render"*. Errors surface at check time with the
exact field path — not at 2am when a customer sees a blank email.

---

## 2. The rendering pipeline

This is the hot path. Every Templane implementation runs these six steps.

```mermaid
flowchart TD
    A[.templane file] --> B[Schema Parser]
    B --> C[Schema AST]

    D[Data JSON/YAML] --> E[Data Loader]
    E --> F[Data tree]

    C --> G{Type Checker}
    F --> G
    G -->|errors| H[/ValidationError list<br/>with field paths/]
    G -->|ok| I[IR Generator]

    J[Template body] --> I
    I --> K[IR tree<br/>typed, resolved]

    K --> L{Adapter}
    L -->|HTML| M[html-escape → string]
    L -->|YAML| N[yaml-emit → string]
    L -->|Engine| O[Jinja/Handlebars/<br/>FreeMarker/text-tmpl]

    M --> P[Output]
    N --> P
    O --> P

    style H fill:#C75B3C,color:#F4EDE0,stroke:#2B2A28
    style P fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
    style K fill:#FBF6EB,stroke:#2B2A28,color:#2B2A28
```

**Key property**: the IR is **post-check**. By the time it reaches any
adapter, the adapter can assume every field access is valid. Adapters
never need to handle missing-field errors.

---

## 3. Per-language anatomy

Every implementation has the same internal layout. This is what makes
conformance possible — identical seams.

```mermaid
flowchart TB
    subgraph lang["templane-{lang}"]
        direction TB
        SP[SchemaParser]
        DL[DataLoader]
        TC[TypeChecker]
        IR[IRGenerator]
        BCD[BreakingChange<br/>Detector]

        subgraph adapters["Output adapters"]
            HTML[html adapter]
            YAML[yaml adapter]
            ENG[engine binding<br/>jinja/handlebars/<br/>freemarker/gotmpl]
        end

        CONF[conform-adapter<br/>stdio bridge]
    end

    SP --> TC
    DL --> TC
    TC --> IR
    IR --> HTML
    IR --> YAML
    IR --> ENG
    SP -.schema A.-> BCD
    SP -.schema B.-> BCD

    IR -.exposed via.-> CONF

    style CONF fill:#D9A441,color:#2B2A28,stroke:#2B2A28
    style BCD fill:#FBF6EB,color:#2B2A28,stroke:#2B2A28
```

**The five implementations**:

| Impl | Language | Engine binding | Package manager |
|---|---|---|---|
| `templane-spec/templane-core` | Python | reference | uv |
| `templane-ts` | TypeScript | handlebars-templane | npm |
| `templane-python` | Python | jinja_templane | uv |
| `templane-java` | Java 21 | freemarker-templane | Gradle → M2 |
| `templane-go` | Go 1.22+ | (pending) gotmpl-templane | go modules |

Each ships its own `conform-adapter` — a stdio bridge that accepts
fixture inputs and returns results in a single canonical JSON shape.

---

## 4. Cross-implementation conformance

This is how we prove the 5 implementations behave identically. 32
fixtures × 5 adapters = **160 checks** on every CI run.

```mermaid
sequenceDiagram
    participant CI as GitHub Actions
    participant Conf as templane-conform CLI<br/>(Node)
    participant Specs as 32 fixtures<br/>(inputs + expected outputs)
    participant Ad as Adapter<br/>(one per impl)

    CI->>Conf: run --adapters spec,ts,py,java,go
    Conf->>Specs: load fixture 1/32
    loop for each fixture
        loop for each adapter
            Conf->>Ad: spawn subprocess
            Conf->>Ad: stdin: {kind, schema, data, template}
            Ad->>Ad: parse → check → IR → render
            Ad-->>Conf: stdout: {ok: true, result: {...}}<br/>or {ok: false, error: {...}}
            Conf->>Conf: diff against fixture.expected
        end
    end
    Conf-->>CI: "5 × 32/32 ✅" or exit non-zero

    Note over Conf,Ad: All comms are line-delimited JSON.<br/>Any language can ship an adapter.
```

**Fixture categories** (under `templane-spec/fixtures/`):

```mermaid
flowchart LR
    F[32 fixtures] --> P[parse]
    F --> C[check]
    F --> I[ir]
    F --> R[render]
    F --> B[breaking]

    P -.7.-> P1[schema parsing edge cases]
    C -.9.-> C1[type-mismatch, missing,<br/>enum, nested, did_you_mean]
    I -.6.-> I1[expr resolution, lists,<br/>nested objects, defaults]
    R -.6.-> R1[HTML escape, YAML emit,<br/>provenance markers]
    B -.4.-> B1[removed_field, required_change,<br/>type_change, enum_value_removed]

    style F fill:#FBF6EB,color:#2B2A28,stroke:#2B2A28
```

---

## 5. The IR (intermediate representation)

The IR is the hand-off shape between the checker and every adapter. It
is a tagged union of node kinds.

```mermaid
classDiagram
    class IRNode {
        <<abstract>>
        +kind: string
    }

    class DocumentNode {
        +kind: "document"
        +children: IRNode[]
    }

    class TextNode {
        +kind: "text"
        +value: string
    }

    class ExprNode {
        +kind: "expr"
        +path: string[]
        +resolved: any
    }

    class IfNode {
        +kind: "if"
        +cond: ExprNode
        +then: IRNode[]
        +else: IRNode[]
    }

    class EachNode {
        +kind: "each"
        +over: ExprNode
        +body: IRNode[]
    }

    class UnlessNode {
        +kind: "unless"
        +cond: ExprNode
        +body: IRNode[]
    }

    IRNode <|-- DocumentNode
    IRNode <|-- TextNode
    IRNode <|-- ExprNode
    IRNode <|-- IfNode
    IRNode <|-- EachNode
    IRNode <|-- UnlessNode

    DocumentNode "1" *-- "many" IRNode : children
    IfNode "1" *-- "many" IRNode : then
    IfNode "1" *-- "many" IRNode : else
    EachNode "1" *-- "many" IRNode : body
    UnlessNode "1" *-- "many" IRNode : body
    IfNode "1" *-- "1" ExprNode : cond
    EachNode "1" *-- "1" ExprNode : over
```

**Why a typed IR and not "just use the engine's AST"?**

- Engines lie about types at render time (every value is a string).
- A shared IR makes cross-engine rendering and conformance tractable.
- The IR carries the *resolved* value on each expression — adapters
  never do a second lookup.

---

## 6. Engine-binding pattern

Every engine binding wraps its native engine with a Templane front door.
The pattern is identical across Jinja, Handlebars, and FreeMarker.

```mermaid
flowchart TB
    U[User code] --> FD[TemplaneConfiguration<br/>.fromFile / .render]
    FD --> SP[SchemaParser]
    FD --> DL[DataLoader]
    SP --> TC[TypeChecker]
    DL --> TC
    TC -->|fails| ERR[/ValidationError/]
    TC -->|ok| IRG[IRGenerator]
    IRG --> BRIDGE[IR → engine model<br/>bridge]

    subgraph engines["native engines"]
        JINJA[Jinja2<br/>Environment]
        HB[Handlebars<br/>runtime]
        FM[FreeMarker<br/>Configuration]
        GT[text/template<br/>pending]
    end

    BRIDGE --> JINJA
    BRIDGE --> HB
    BRIDGE --> FM
    BRIDGE --> GT

    JINJA --> OUT[Output]
    HB --> OUT
    FM --> OUT
    GT --> OUT

    style ERR fill:#C75B3C,color:#F4EDE0,stroke:#2B2A28
    style OUT fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
    style BRIDGE fill:#D9A441,color:#2B2A28,stroke:#2B2A28
```

**What the bridge does**: map the Templane IR + resolved values into
the shape each engine expects (Jinja's context dict, Handlebars' JSON
context, FreeMarker's `TemplateModel`). Engines still do the actual
string concatenation; they just do it on pre-validated, pre-resolved
data.

---

## 7. xt CLI pipeline

`xt` is the developer-facing CLI in `templane-ts`. One binary, five
subcommands, one pipeline.

```mermaid
flowchart TB
    CLI([xt &lt;subcmd&gt;]) --> SW{subcommand}

    SW -->|render| R1[load schema + data] --> R2[check] --> R3[IR gen] --> R4[adapter] --> R5[stdout or file]
    SW -->|check| C1[load schema + data] --> C2[check] --> C3{ok?}
    C3 -->|yes| C4[exit 0]
    C3 -->|no| C5[/errors to stderr<br/>exit 1/]

    SW -->|test| T1[discover fixtures under tests/] --> T2[run each through pipeline] --> T3[diff vs expected] --> T4[report]

    SW -->|dev| D1[chokidar watch *.templane + data] --> D2[debounce 50ms] --> D3[re-render] --> D4[report to tty]

    SW -->|build| B1[discover manifest.json] --> B2[for each entry: render] --> B3[emit to dist/] --> B4[summary]

    style C5 fill:#C75B3C,color:#F4EDE0,stroke:#2B2A28
    style C4 fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
    style T4 fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
    style D4 fill:#FBF6EB,color:#2B2A28,stroke:#2B2A28
    style B4 fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
```

---

## 8. Schema evolution & breaking-change detection

When a `.templane` schema changes, the `BreakingChangeDetector` compares
old vs new and classifies the diff.

```mermaid
flowchart TD
    V1[.templane v1<br/>schema] --> DIFF{BreakingChange<br/>Detector}
    V2[.templane v2<br/>schema] --> DIFF

    DIFF --> C1[removed_field]
    DIFF --> C2[required_change]
    DIFF --> C3[type_change]
    DIFF --> C4[enum_value_removed]
    DIFF --> C5[safe additions]

    C1 --> BRK[/breaking:<br/>downstream data<br/>will fail validation/]
    C2 --> BRK
    C3 --> BRK
    C4 --> BRK
    C5 --> OK[/safe:<br/>ship without coordination/]

    BRK --> FLOW[CI annotates PR<br/>⚠️ n downstream files<br/>affected]
    OK --> GREEN[CI green]

    style BRK fill:#C75B3C,color:#F4EDE0,stroke:#2B2A28
    style OK fill:#4A6B52,color:#F4EDE0,stroke:#2B2A28
    style FLOW fill:#D9A441,color:#2B2A28,stroke:#2B2A28
```

**Category reference**:

| Category | Example | Impact |
|---|---|---|
| `removed_field` | schema had `email`, now doesn't | Data files still sending `email` → `unknown_field` error |
| `required_change` | optional → required | Data files missing the field → `missing_required_field` |
| `type_change` | string → number | Every data file → `type_mismatch` |
| `enum_value_removed` | `[A, B, C]` → `[A, B]` | Any data using `C` → `invalid_enum_value` |

Safe changes (never flagged): new optional fields, new enum values added,
relaxed constraints (required → optional).

---

## 9. Publishing topology

How each artifact flows from the monorepo to its registry.

```mermaid
flowchart LR
    MONO[[github.com/ereshzealous/Templane]]

    MONO --> N1[templane-ts]
    MONO --> N2[templane-conform CLI]
    MONO --> PY1[templane-python]
    MONO --> PY2[templane-core]
    MONO --> J1[templane-java artifacts]
    MONO --> G1[templane-go]

    N1 -->|npm publish<br/>workflow_dispatch| NPM[(npm<br/>registry)]
    N2 -->|npm publish| NPM
    PY1 -->|OIDC trusted pub| PYPI[(PyPI)]
    PY2 -->|OIDC trusted pub| PYPI
    J1 -->|mvn/gradle publish<br/>3 modes| MAVEN[(Maven Central<br/>or GitHub Packages)]
    G1 -->|git tag<br/>templane-go/vX.Y.Z| PROXY[(proxy.golang.org<br/>no registry push)]

    style NPM fill:#D9A441,color:#2B2A28,stroke:#2B2A28
    style PYPI fill:#D9A441,color:#2B2A28,stroke:#2B2A28
    style MAVEN fill:#D9A441,color:#2B2A28,stroke:#2B2A28
    style PROXY fill:#D9A441,color:#2B2A28,stroke:#2B2A28
    style MONO fill:#FBF6EB,color:#2B2A28,stroke:#2B2A28
```

**Trigger model**: every release workflow is `workflow_dispatch` only —
no auto-publish on push or tag. Each workflow has a dry-run flag.

---

## Where to go next

- [`SPEC.md`](../SPEC.md) — the normative protocol spec (RFC 2119 keywords, fixture-referenced).
- [`CONTRIBUTING.md`](../CONTRIBUTING.md) — adding a language binding, adding an engine integration.
- [`examples/`](../examples/) — six progressive tiers from hello-world to Helm chart validation.
- [`.github/workflows/README.md`](../.github/workflows/README.md) — CI and release workflow reference.
