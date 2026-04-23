# tsp-spec Design

**Date:** 2026-04-23  
**Scope:** Phase 1 — `tsp-spec` (32 fixtures + tsp-conform CLI + Python reference implementation)  
**Build strategy:** Approach C — category-by-category (8 fixtures per category, validate incrementally)

---

## 1. Directory Structure

```
tsp/
└── tsp-spec/
    ├── fixtures/
    │   ├── schema-parser/        (8 JSON fixture files)
    │   ├── type-checker/         (8 JSON fixture files)
    │   ├── ir-generator/         (8 JSON fixture files)
    │   └── adapters/             (8 JSON fixture files)
    │
    ├── tsp-core/                 ← Python reference implementation
    │   ├── pyproject.toml
    │   └── src/tsp_core/
    │       ├── models.py
    │       ├── schema_parser.py
    │       ├── type_checker.py
    │       ├── ir_generator.py
    │       ├── html_adapter.py
    │       └── yaml_adapter.py
    │
    ├── conform-adapter/          ← Python subprocess shim
    │   └── run.py
    │
    ├── tsp-conform/              ← Node.js TypeScript CLI
    │   ├── package.json
    │   ├── tsconfig.json
    │   └── src/
    │       └── cli.ts
    │
    └── tsp-conform.yaml          ← adapter registry
```

`tsp-conform.yaml` format:
```yaml
adapters:
  spec: python3 tsp-spec/conform-adapter/run.py
```

Each fixture file shape:
```json
{
  "fixture_id": "schema-parser/basic",
  "input": { ... },
  "expected_output": { ... }
}
```

---

## 2. Data Flow

```
Fixture file (JSON)
  → tsp-conform CLI reads fixture_id + input
  → sends {"fixture_id": "...", "fixture": {input}} to adapter stdin (one JSON line)
  → adapter routes by fixture_id prefix:
       schema-parser/*   → schema_parser.parse(yaml_string)          → TypedSchema JSON
       type-checker/*    → type_checker.check(schema, data)          → {"errors": [...]}
       ir-generator/*    → ir_generator.generate(ast, data, ...)     → TIR JSON
       adapters/html-*   → html_adapter.render(tir)                  → {"output": "...html..."}
       adapters/yaml-*   → yaml_adapter.render(tir)                  → {"output": "...yaml..."}
  → adapter writes {"passed": true, "output": {...}} or {"passed": false, "error": "..."} to stdout
  → CLI deep-equals adapter output against fixture expected_output
  → reports pass/fail per fixture, final tally "spec N/32"
```

**Key invariants:**
- Adapter subprocess stays alive for all 32 fixtures (reads stdin in a loop until EOF)
- Each message is exactly one line of JSON (no pretty-printing)
- No startup/teardown messages — first stdin line is the first fixture

---

## 3. Python Models (`models.py`)

```python
# Type hierarchy
@dataclass class StringType: pass
@dataclass class NumberType: pass
@dataclass class BooleanType: pass
@dataclass class NullType: pass
@dataclass class EnumType:    values: list[str]
@dataclass class ListType:    item_type: 'TSPFieldType'
@dataclass class ObjectType:  fields: dict[str, 'TSPField']

TSPFieldType = StringType | NumberType | BooleanType | NullType | EnumType | ListType | ObjectType

@dataclass class TSPField:
    name: str
    type: TSPFieldType
    required: bool

@dataclass class TypedSchema:
    id: str
    fields: dict[str, TSPField]

@dataclass class TypeCheckError:
    code: str   # missing_required_field | type_mismatch | invalid_enum_value | unknown_field | did_you_mean
    field: str
    message: str

# AST nodes
@dataclass class TextNode:    content: str
@dataclass class ExprNode:    field: str
@dataclass class Condition:   op: str; left: str; right: Any
@dataclass class IfNode:      condition: Condition; then_branch: list; else_branch: list
@dataclass class ForEachNode: var: str; iterable: str; body: list
ASTNode = TextNode | ExprNode | IfNode | ForEachNode

# TIR nodes
@dataclass class TIRTextNode:    content: str
@dataclass class TIRExprNode:    field: str; resolved: Any
@dataclass class TIRIfNode:      condition: bool; branch: list
@dataclass class TIRForeachNode: var: str; items: list[list]
TIRNode = TIRTextNode | TIRExprNode | TIRIfNode | TIRForeachNode

@dataclass class TIRResult:
    template_id: str
    schema_id: str
    nodes: list[TIRNode]
```

JSON serialization: custom `to_dict()` / `from_dict()` (no external deps beyond `pyyaml`).

---

## 3b. conform-adapter venv bootstrap (`run.py`)

`tsp-conform` spawns `python3 run.py` using the system Python, not the venv Python. `run.py` must inject the venv's site-packages at startup:

```python
from pathlib import Path
import sys

_repo = Path(__file__).resolve().parent.parent
for _sp in (_repo / "tsp-core" / ".venv" / "lib").glob("python*/site-packages"):
    if str(_sp) not in sys.path:
        sys.path.insert(1, str(_sp))
```

This is required — without it, `import tsp_core` fails when spawned by the CLI.

---

## 4. The 32 Fixtures

### Category 1: schema-parser (8 fixtures)
Input: `{yaml: "..."}` — Output: TypedSchema JSON

| Fixture | Key behavior |
|---|---|
| `basic` | string + number + boolean fields → TypedSchema with correct types |
| `required-fields` | mix of `required: true/false` → TSPField.required preserved |
| `enum-type` | `type: enum, values: [a,b,c]` → EnumType with values list |
| `list-type` | `type: list, items: {type: string}` → ListType(StringType) |
| `object-type` | nested `type: object, fields: {...}` → ObjectType with sub-fields |
| `body-extracted` | YAML with `---` separator → output has both `schema` AND `body` keys |
| `invalid-schema` | malformed YAML → output has `error` key, no `schema` key |
| `deep-nesting` | 3-level nested objects → fully recursive ObjectType |

### Category 2: type-checker (8 fixtures)
Input: `{schema: TypedSchema, data: {...}}` — Output: `{errors: [...]}`

| Fixture | Key behavior |
|---|---|
| `valid-data` | all fields correct → `errors: []` |
| `missing-required` | required field absent → `missing_required_field` error |
| `type-mismatch` | string where number expected → `type_mismatch` error |
| `invalid-enum` | value not in enum set → `invalid_enum_value` error |
| `unknown-field` | extra field not in schema → `unknown_field` error |
| `did-you-mean` | `naem` vs `name` (Levenshtein ≤ 3) → suggestion in error |
| `nested-object` | type error inside nested object → error with dotted field path |
| `list-type-mismatch` | list item wrong type → `type_mismatch` at `tags[0]` |

### Category 3: ir-generator (8 fixtures)
Input: `{ast: [...], data: {...}, schema_id: "...", template_id: "..."}` — Output: TIR JSON

| Fixture | Key behavior |
|---|---|
| `basic-expr` | ExprNode("name") + data {name:"Alice"} → TIRExprNode(resolved="Alice") |
| `missing-path` | field absent from data → TIRExprNode(resolved=null) — never throws |
| `if-true` | condition true → then_branch in TIRIfNode |
| `if-false` | condition false → else_branch (may be empty list) |
| `foreach-items` | ForEachNode over 3-item list → TIRForeachNode with 3 rendered bodies |
| `nested-path` | ExprNode("user.address.city") → resolved via dotted path walk |
| `condition-equals` | op "==" condition → bool evaluated, stored in TIRIfNode.condition |
| `provenance` | output TIR has template_id and schema_id at top level |

### Category 4: adapters (8 fixtures)
Input: `{tir: TIRResult}` — Output: `{output: "...string..."}`

| Fixture | Key behavior |
|---|---|
| `html-basic` | text + expr nodes → rendered HTML string |
| `html-special-chars` | expr value `"<b>&"` → `"&lt;b&gt;&amp;"` (HTML-escaped) |
| `html-provenance` | output starts with `<!-- tsp template_id=... schema_id=... -->` |
| `yaml-basic` | TIR → YAML string, expr values NOT escaped |
| `yaml-provenance` | output starts with `# tsp template_id=... schema_id=...` |
| `html-falsy-zero` | expr resolved to `0` → renders as `"0"`, not blank |
| `html-foreach` | TIRForeachNode → each iteration rendered and concatenated in HTML |
| `yaml-foreach` | TIRForeachNode → each iteration rendered and concatenated in YAML |

---

## 5. Key Invariants (from spec)

- **Missing path → null, never throw:** IR generator resolves missing dotted paths to null.
- **Falsy values render as strings:** `0` → `"0"`, `false` → `"false"`. Only `None` → `""`.
- **Type errors are collected, not thrown:** type checker accumulates all errors, no short-circuit.
- **HTML escapes; YAML does not:** HTML adapter applies entity escaping to all expr values.
- **body-extracted must return both keys:** schema parser output includes `schema` AND `body`.
- **Levenshtein ≤ 3 triggers did-you-mean:** computed against all known schema field names.

---

## 6. Build Order (Approach C — category-by-category)

1. Scaffold `tsp-spec/` directory structure + `tsp-conform.yaml`
2. Set up `tsp-core/` Python project (pyproject.toml, uv)
3. Set up `tsp-conform/` Node.js project (package.json, tsconfig.json)
4. **Category 1:** Write schema-parser fixtures + `models.py` + `schema_parser.py` + CLI routing → verify `spec 8/32`
5. **Category 2:** Write type-checker fixtures + `type_checker.py` → verify `spec 16/32`
6. **Category 3:** Write ir-generator fixtures + `ir_generator.py` → verify `spec 24/32`
7. **Category 4:** Write adapter fixtures + `html_adapter.py` + `yaml_adapter.py` → verify `spec 32/32`
