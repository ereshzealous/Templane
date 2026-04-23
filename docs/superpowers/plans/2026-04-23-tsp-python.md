# tsp-python Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `tsp-python` — a separate production Python package achieving `py 32/32` on `tsp-conform`, with additional features (breaking-change detector, schema hash, Jinja2 integration) beyond the reference implementation in `tsp-spec/tsp-core`.

**Architecture:** Category-by-category conformance (mirrors Phases 1–2), then three add-on features. The core logic is structurally identical to `tsp-spec/tsp-core` (same YAML schema parser, type checker, IR generator, adapters); this plan includes the full code verbatim so subagents don't need to read the spec repo.

**Tech Stack:** Python 3.12+, `uv` for project/venv management, `hatchling` build backend, `pyyaml` for YAML, `jinja2` for template engine, `pytest` for tests. Single `pyproject.toml` with multiple packages under `src/`.

---

## File Map

```
tsp-python/
├── pyproject.toml
├── src/
│   ├── tsp_core/
│   │   ├── __init__.py
│   │   ├── models.py            ← dataclasses + to_dict/from_dict serialization
│   │   ├── schema_parser.py     ← YAML → TypedSchema
│   │   ├── type_checker.py      ← validation + Levenshtein did-you-mean
│   │   ├── ir_generator.py      ← AST walk → TIR
│   │   ├── breaking_change.py   ← schema evolution detector (NEW)
│   │   └── hash.py              ← SHA-256 schema hash (NEW)
│   ├── tsp_adapter_html/
│   │   ├── __init__.py
│   │   └── html_adapter.py      ← TIR → HTML
│   ├── tsp_adapter_yaml/
│   │   ├── __init__.py
│   │   └── yaml_adapter.py      ← TIR → YAML
│   └── jinja_tsp/
│       ├── __init__.py
│       └── environment.py       ← TSPEnvironment wrapping jinja2 (NEW)
├── tests/
│   ├── __init__.py
│   ├── test_models.py
│   ├── test_schema_parser.py
│   ├── test_type_checker.py
│   ├── test_ir_generator.py
│   ├── test_adapters.py
│   ├── test_breaking_change.py
│   ├── test_hash.py
│   └── test_jinja_tsp.py
└── conform-adapter/
    └── run.py                   ← subprocess shim; venv bootstrap via site.addsitedir()
```

Fixtures live in `tsp-spec/fixtures/` (re-used from Phase 1). The conform-adapter is the subprocess invoked by `tsp-conform` when running `--adapters "py:python3 tsp-python/conform-adapter/run.py"`.

---

## Task 1: Scaffold tsp-python project

**Files:**
- Create: `tsp-python/pyproject.toml`
- Create: `tsp-python/src/tsp_core/__init__.py`
- Create: `tsp-python/src/tsp_adapter_html/__init__.py`
- Create: `tsp-python/src/tsp_adapter_yaml/__init__.py`
- Create: `tsp-python/src/jinja_tsp/__init__.py`
- Create: `tsp-python/tests/__init__.py`

- [ ] **Step 1: Create directories**

```bash
mkdir -p tsp-python/src/tsp_core
mkdir -p tsp-python/src/tsp_adapter_html
mkdir -p tsp-python/src/tsp_adapter_yaml
mkdir -p tsp-python/src/jinja_tsp
mkdir -p tsp-python/tests
mkdir -p tsp-python/conform-adapter
```

- [ ] **Step 2: Create `tsp-python/pyproject.toml`**

```toml
[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

[project]
name = "tsp-python"
version = "0.1.0"
requires-python = ">=3.12"
dependencies = ["pyyaml>=6.0", "jinja2>=3.1"]

[project.optional-dependencies]
dev = ["pytest>=8.0"]

[tool.hatch.build.targets.wheel]
packages = [
  "src/tsp_core",
  "src/tsp_adapter_html",
  "src/tsp_adapter_yaml",
  "src/jinja_tsp",
]

[tool.pytest.ini_options]
testpaths = ["tests"]
```

- [ ] **Step 3: Create empty `__init__.py` files**

```bash
touch tsp-python/src/tsp_core/__init__.py
touch tsp-python/src/tsp_adapter_html/__init__.py
touch tsp-python/src/tsp_adapter_yaml/__init__.py
touch tsp-python/src/jinja_tsp/__init__.py
touch tsp-python/tests/__init__.py
```

- [ ] **Step 4: Set up venv and install deps**

```bash
cd tsp-python && uv sync --extra dev
```

Expected: `.venv/` created, `pyyaml`, `jinja2`, and `pytest` installed.

- [ ] **Step 5: Verify pytest collects zero tests cleanly**

```bash
cd tsp-python && .venv/bin/pytest --collect-only 2>&1 | tail -5
```

Expected: `no tests ran` or `collected 0 items`, no errors.

- [ ] **Step 6: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/pyproject.toml tsp-python/src/ tsp-python/tests/
git commit -m "chore: scaffold tsp-python project"
```

Do NOT commit `.venv/` or `conform-adapter/` yet (those come later).

---

## Task 2: tsp_core/models.py + tests

**Files:**
- Create: `tsp-python/src/tsp_core/models.py`
- Create: `tsp-python/tests/test_models.py`

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_models.py`:

```python
from tsp_core.models import (
    StringType, NumberType, BooleanType, EnumType, ListType, ObjectType,
    TSPField, TypedSchema, TypeCheckError,
    TextNode, ExprNode, Condition, IfNode, ForEachNode,
    TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode, TIRResult,
    tsp_field_type_to_dict, tsp_field_type_from_dict,
    tsp_field_to_dict, tsp_field_from_dict,
    typed_schema_to_dict, typed_schema_from_dict,
    ast_node_to_dict, ast_node_from_dict,
    tir_node_to_dict, tir_node_from_dict,
    tir_result_to_dict, tir_result_from_dict,
)


def test_string_type_roundtrip():
    t = StringType()
    assert tsp_field_type_from_dict(tsp_field_type_to_dict(t)) == t


def test_enum_type_roundtrip():
    t = EnumType(values=["a", "b", "c"])
    assert tsp_field_type_from_dict(tsp_field_type_to_dict(t)) == t


def test_list_type_roundtrip():
    t = ListType(item_type=StringType())
    assert tsp_field_type_from_dict(tsp_field_type_to_dict(t)) == t


def test_object_type_roundtrip():
    t = ObjectType(fields={"city": TSPField(name="city", type=StringType(), required=True)})
    assert tsp_field_type_from_dict(tsp_field_type_to_dict(t)) == t


def test_typed_schema_roundtrip():
    schema = TypedSchema(
        id="test",
        fields={"name": TSPField(name="name", type=StringType(), required=True)},
    )
    assert typed_schema_from_dict(typed_schema_to_dict(schema)) == schema


def test_tir_result_roundtrip():
    tir = TIRResult(
        template_id="tmpl",
        schema_id="sch",
        nodes=[TIRTextNode(content="Hello"), TIRExprNode(field="name", resolved="Alice")],
    )
    assert tir_result_from_dict(tir_result_to_dict(tir)) == tir


def test_ast_node_roundtrip():
    nodes = [
        TextNode(content="Hi"),
        ExprNode(field="name"),
        IfNode(
            condition=Condition(op="==", left="x", right="1"),
            then_branch=[TextNode(content="yes")],
            else_branch=[],
        ),
        ForEachNode(var="item", iterable="items", body=[ExprNode(field="item")]),
    ]
    for node in nodes:
        assert ast_node_from_dict(ast_node_to_dict(node)) == node
```

- [ ] **Step 2: Run to see failure**

```bash
cd tsp-python && .venv/bin/pytest tests/test_models.py -v 2>&1 | tail -10
```

Expected: `ImportError` / `ModuleNotFoundError`. `uv sync` in Task 1 already installed the project editable, so the package IS importable — but the specific symbols aren't defined yet. Either failure mode counts as "red".

- [ ] **Step 3: Create `tsp-python/src/tsp_core/models.py`** (identical to `tsp-spec/tsp-core`):

```python
from __future__ import annotations
from dataclasses import dataclass
from typing import Any


# ---------------------------------------------------------------------------
# TSPFieldType hierarchy
# ---------------------------------------------------------------------------

@dataclass(eq=True)
class StringType:
    pass


@dataclass(eq=True)
class NumberType:
    pass


@dataclass(eq=True)
class BooleanType:
    pass


@dataclass(eq=True)
class NullType:
    pass


@dataclass(eq=True)
class EnumType:
    values: list[str]


@dataclass(eq=True)
class ListType:
    item_type: TSPFieldType


@dataclass(eq=True)
class ObjectType:
    fields: dict[str, TSPField]


TSPFieldType = StringType | NumberType | BooleanType | NullType | EnumType | ListType | ObjectType


@dataclass(eq=True)
class TSPField:
    name: str
    type: TSPFieldType
    required: bool


@dataclass(eq=True)
class TypedSchema:
    id: str
    fields: dict[str, TSPField]


@dataclass(eq=True)
class TypeCheckError:
    code: str
    field: str
    message: str

    def to_dict(self) -> dict:
        return {"code": self.code, "field": self.field, "message": self.message}


# ---------------------------------------------------------------------------
# AST nodes
# ---------------------------------------------------------------------------

@dataclass(eq=True)
class TextNode:
    content: str


@dataclass(eq=True)
class ExprNode:
    field: str


@dataclass(eq=True)
class Condition:
    op: str
    left: str
    right: Any


@dataclass(eq=True)
class IfNode:
    condition: Condition
    then_branch: list
    else_branch: list


@dataclass(eq=True)
class ForEachNode:
    var: str
    iterable: str
    body: list


ASTNode = TextNode | ExprNode | IfNode | ForEachNode


# ---------------------------------------------------------------------------
# TIR nodes
# ---------------------------------------------------------------------------

@dataclass(eq=True)
class TIRTextNode:
    content: str


@dataclass(eq=True)
class TIRExprNode:
    field: str
    resolved: Any


@dataclass(eq=True)
class TIRIfNode:
    condition: bool
    branch: list


@dataclass(eq=True)
class TIRForeachNode:
    var: str
    items: list


@dataclass(eq=True)
class TIRResult:
    template_id: str
    schema_id: str
    nodes: list

    def to_dict(self) -> dict:
        return tir_result_to_dict(self)


TIRNode = TIRTextNode | TIRExprNode | TIRIfNode | TIRForeachNode


# ---------------------------------------------------------------------------
# Serialization: TSPFieldType
# ---------------------------------------------------------------------------

def tsp_field_type_to_dict(t: TSPFieldType) -> dict:
    if isinstance(t, StringType):
        return {"kind": "string"}
    if isinstance(t, NumberType):
        return {"kind": "number"}
    if isinstance(t, BooleanType):
        return {"kind": "boolean"}
    if isinstance(t, NullType):
        return {"kind": "null"}
    if isinstance(t, EnumType):
        return {"kind": "enum", "values": list(t.values)}
    if isinstance(t, ListType):
        return {"kind": "list", "item_type": tsp_field_type_to_dict(t.item_type)}
    if isinstance(t, ObjectType):
        return {
            "kind": "object",
            "fields": {k: tsp_field_to_dict(v) for k, v in t.fields.items()},
        }
    raise ValueError(f"Unknown type: {t}")


def tsp_field_type_from_dict(d: dict) -> TSPFieldType:
    kind = d["kind"]
    if kind == "string":
        return StringType()
    if kind == "number":
        return NumberType()
    if kind == "boolean":
        return BooleanType()
    if kind == "null":
        return NullType()
    if kind == "enum":
        return EnumType(values=d["values"])
    if kind == "list":
        return ListType(item_type=tsp_field_type_from_dict(d["item_type"]))
    if kind == "object":
        fields = {k: tsp_field_from_dict(v) for k, v in d["fields"].items()}
        return ObjectType(fields=fields)
    raise ValueError(f"Unknown kind: {kind}")


# ---------------------------------------------------------------------------
# Serialization: TSPField, TypedSchema
# ---------------------------------------------------------------------------

def tsp_field_to_dict(f: TSPField) -> dict:
    return {"name": f.name, "type": tsp_field_type_to_dict(f.type), "required": f.required}


def tsp_field_from_dict(d: dict) -> TSPField:
    return TSPField(name=d["name"], type=tsp_field_type_from_dict(d["type"]), required=d["required"])


def typed_schema_to_dict(s: TypedSchema) -> dict:
    return {"id": s.id, "fields": {k: tsp_field_to_dict(v) for k, v in s.fields.items()}}


def typed_schema_from_dict(d: dict) -> TypedSchema:
    fields = {k: tsp_field_from_dict(v) for k, v in d["fields"].items()}
    return TypedSchema(id=d["id"], fields=fields)


# ---------------------------------------------------------------------------
# Serialization: AST nodes
# ---------------------------------------------------------------------------

def ast_node_to_dict(node: ASTNode) -> dict:
    if isinstance(node, TextNode):
        return {"kind": "text", "content": node.content}
    if isinstance(node, ExprNode):
        return {"kind": "expr", "field": node.field}
    if isinstance(node, IfNode):
        return {
            "kind": "if",
            "condition": {"op": node.condition.op, "left": node.condition.left, "right": node.condition.right},
            "then_branch": [ast_node_to_dict(n) for n in node.then_branch],
            "else_branch": [ast_node_to_dict(n) for n in node.else_branch],
        }
    if isinstance(node, ForEachNode):
        return {
            "kind": "foreach",
            "var": node.var,
            "iterable": node.iterable,
            "body": [ast_node_to_dict(n) for n in node.body],
        }
    raise ValueError(f"Unknown node: {node}")


def ast_node_from_dict(d: dict) -> ASTNode:
    kind = d["kind"]
    if kind == "text":
        return TextNode(content=d["content"])
    if kind == "expr":
        return ExprNode(field=d["field"])
    if kind == "if":
        cond = Condition(op=d["condition"]["op"], left=d["condition"]["left"], right=d["condition"]["right"])
        return IfNode(
            condition=cond,
            then_branch=[ast_node_from_dict(n) for n in d["then_branch"]],
            else_branch=[ast_node_from_dict(n) for n in d["else_branch"]],
        )
    if kind == "foreach":
        return ForEachNode(
            var=d["var"],
            iterable=d["iterable"],
            body=[ast_node_from_dict(n) for n in d["body"]],
        )
    raise ValueError(f"Unknown kind: {kind}")


# ---------------------------------------------------------------------------
# Serialization: TIR nodes
# ---------------------------------------------------------------------------

def tir_node_to_dict(node: TIRNode) -> dict:
    if isinstance(node, TIRTextNode):
        return {"kind": "text", "content": node.content}
    if isinstance(node, TIRExprNode):
        return {"kind": "expr", "field": node.field, "resolved": node.resolved}
    if isinstance(node, TIRIfNode):
        return {"kind": "if", "condition": node.condition, "branch": [tir_node_to_dict(n) for n in node.branch]}
    if isinstance(node, TIRForeachNode):
        return {
            "kind": "foreach",
            "var": node.var,
            "items": [[tir_node_to_dict(n) for n in item] for item in node.items],
        }
    raise ValueError(f"Unknown node: {node}")


def tir_node_from_dict(d: dict) -> TIRNode:
    kind = d["kind"]
    if kind == "text":
        return TIRTextNode(content=d["content"])
    if kind == "expr":
        return TIRExprNode(field=d["field"], resolved=d["resolved"])
    if kind == "if":
        return TIRIfNode(condition=d["condition"], branch=[tir_node_from_dict(n) for n in d["branch"]])
    if kind == "foreach":
        items = [[tir_node_from_dict(n) for n in item] for item in d["items"]]
        return TIRForeachNode(var=d["var"], items=items)
    raise ValueError(f"Unknown kind: {kind}")


def tir_result_to_dict(r: TIRResult) -> dict:
    return {
        "template_id": r.template_id,
        "schema_id": r.schema_id,
        "nodes": [tir_node_to_dict(n) for n in r.nodes],
    }


def tir_result_from_dict(d: dict) -> TIRResult:
    return TIRResult(
        template_id=d["template_id"],
        schema_id=d["schema_id"],
        nodes=[tir_node_from_dict(n) for n in d["nodes"]],
    )
```

- [ ] **Step 4: Run tests**

```bash
cd tsp-python && .venv/bin/pytest tests/test_models.py -v
```

Expected: 7 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_core/models.py tsp-python/tests/test_models.py
git commit -m "feat: tsp-python tsp_core models"
```

---

## Task 3: tsp_core/schema_parser.py + tests

**Files:**
- Create: `tsp-python/src/tsp_core/schema_parser.py`
- Create: `tsp-python/tests/test_schema_parser.py`

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_schema_parser.py`:

```python
from tsp_core.schema_parser import parse


def test_basic_fields():
    result = parse("name:\n  type: string\n  required: true\nage:\n  type: number\n  required: false\n", "basic")
    schema = result["schema"]
    assert schema["id"] == "basic"
    assert schema["fields"]["name"]["type"] == {"kind": "string"}
    assert schema["fields"]["name"]["required"] is True
    assert schema["fields"]["age"]["type"] == {"kind": "number"}
    assert schema["fields"]["age"]["required"] is False


def test_enum_type():
    yaml = "status:\n  type: enum\n  values: [active, inactive, pending]\n  required: true\n"
    result = parse(yaml, "enum-type")
    assert result["schema"]["fields"]["status"]["type"] == {
        "kind": "enum", "values": ["active", "inactive", "pending"]
    }


def test_list_type():
    yaml = "tags:\n  type: list\n  items:\n    type: string\n  required: false\n"
    result = parse(yaml, "list-type")
    assert result["schema"]["fields"]["tags"]["type"] == {
        "kind": "list", "item_type": {"kind": "string"}
    }


def test_object_type():
    yaml = "address:\n  type: object\n  required: true\n  fields:\n    city:\n      type: string\n      required: true\n"
    result = parse(yaml, "object-type")
    obj_type = result["schema"]["fields"]["address"]["type"]
    assert obj_type["kind"] == "object"
    assert obj_type["fields"]["city"]["type"] == {"kind": "string"}


def test_body_extracted():
    yaml = "name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n"
    result = parse(yaml, "body-extracted")
    assert "schema" in result
    assert result["body"] == "Hello {{ name }}!\n"


def test_invalid_schema_returns_error():
    result = parse("- just\n- a\n- list\n", "invalid-schema")
    assert "error" in result
    assert "schema" not in result


def test_deep_nesting():
    yaml = (
        "order:\n"
        "  type: object\n"
        "  required: true\n"
        "  fields:\n"
        "    customer:\n"
        "      type: object\n"
        "      required: true\n"
        "      fields:\n"
        "        address:\n"
        "          type: object\n"
        "          required: true\n"
        "          fields:\n"
        "            city:\n"
        "              type: string\n"
        "              required: true\n"
    )
    result = parse(yaml, "deep-nesting")
    outer = result["schema"]["fields"]["order"]["type"]
    mid = outer["fields"]["customer"]["type"]
    inner = mid["fields"]["address"]["type"]
    assert inner["fields"]["city"]["type"] == {"kind": "string"}
```

- [ ] **Step 2: Run to see failure**

```bash
cd tsp-python && .venv/bin/pytest tests/test_schema_parser.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError for `tsp_core.schema_parser`.

- [ ] **Step 3: Create `tsp-python/src/tsp_core/schema_parser.py`**

```python
from __future__ import annotations
import yaml
from .models import (
    TSPField, TSPFieldType, TypedSchema,
    StringType, NumberType, BooleanType, NullType, EnumType, ListType, ObjectType,
    typed_schema_to_dict,
)


def parse(yaml_str: str, schema_id: str) -> dict:
    body: str | None = None

    if "\n---\n" in yaml_str:
        parts = yaml_str.split("\n---\n", 1)
        schema_yaml = parts[0]
        body = parts[1]
    else:
        schema_yaml = yaml_str

    try:
        data = yaml.safe_load(schema_yaml)
    except yaml.YAMLError as exc:
        return {"error": str(exc)}

    if not isinstance(data, dict):
        return {"error": "Schema must be a YAML mapping"}

    fields: dict[str, TSPField] = {}
    for name, field_def in data.items():
        fields[name] = _parse_field(name, field_def or {})

    schema = TypedSchema(id=schema_id, fields=fields)
    result: dict = {"schema": typed_schema_to_dict(schema)}
    if body is not None:
        result["body"] = body
    return result


def _parse_field(name: str, field_def: dict) -> TSPField:
    type_str = field_def.get("type", "string")
    required = bool(field_def.get("required", False))
    return TSPField(name=name, type=_parse_type(type_str, field_def), required=required)


def _parse_type(type_str: str, field_def: dict) -> TSPFieldType:
    if type_str == "string":
        return StringType()
    if type_str == "number":
        return NumberType()
    if type_str == "boolean":
        return BooleanType()
    if type_str == "null":
        return NullType()
    if type_str == "enum":
        values = [str(v) for v in field_def.get("values", [])]
        return EnumType(values=values)
    if type_str == "list":
        items_def = field_def.get("items") or {}
        item_type = _parse_type(items_def.get("type", "string"), items_def)
        return ListType(item_type=item_type)
    if type_str == "object":
        sub_fields: dict[str, TSPField] = {}
        for fname, fdef in (field_def.get("fields") or {}).items():
            sub_fields[fname] = _parse_field(fname, fdef or {})
        return ObjectType(fields=sub_fields)
    return StringType()
```

- [ ] **Step 4: Run tests**

```bash
cd tsp-python && .venv/bin/pytest tests/test_schema_parser.py -v
```

Expected: 7 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_core/schema_parser.py tsp-python/tests/test_schema_parser.py
git commit -m "feat: tsp-python tsp_core schema parser"
```

---

## Task 4: conform-adapter (schema-parser) → py 8/32

**Files:**
- Create: `tsp-python/conform-adapter/run.py`

- [ ] **Step 1: Create `tsp-python/conform-adapter/run.py`**

```python
#!/usr/bin/env python3
"""tsp-python adapter — routes fixture IDs to tsp-python handlers."""
from pathlib import Path
import sys
import site
import json

# Bootstrap tsp-python venv into system Python's path.
_repo = Path(__file__).resolve().parent.parent
for _sp in (_repo / ".venv" / "lib").glob("python*/site-packages"):
    site.addsitedir(str(_sp))

from tsp_core.schema_parser import parse as schema_parse  # noqa: E402


def handle(fixture_id: str, fixture: dict) -> dict:
    try:
        if fixture_id.startswith("schema-parser"):
            result = schema_parse(fixture["yaml"], fixture.get("id", "unknown"))
            return {"output": result}
        return {"output": None, "error": f"Unhandled fixture: {fixture_id}"}
    except Exception as exc:
        return {"output": None, "error": str(exc)}


def main() -> None:
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        req = json.loads(raw)
        result = handle(req["fixture_id"], req["fixture"])
        print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run tsp-conform — expect py 8/32**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
node tsp-spec/tsp-conform/dist/cli.js --adapters "py:python3 tsp-python/conform-adapter/run.py" 2>&1 | grep -E "py:"
```

Expected: `py: 8/32`.

- [ ] **Step 3: Commit**

```bash
git add tsp-python/conform-adapter/run.py
git commit -m "feat: tsp-python conform-adapter schema-parser routing — py 8/32"
```

---

## Task 5: tsp_core/type_checker.py + tests

**Files:**
- Create: `tsp-python/src/tsp_core/type_checker.py`
- Create: `tsp-python/tests/test_type_checker.py`

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_type_checker.py`:

```python
from tsp_core.type_checker import check
from tsp_core.models import (
    TypedSchema, TSPField, StringType, NumberType, BooleanType,
    EnumType, ListType, ObjectType,
)


def _schema(*pairs):
    fields = {name: TSPField(name=name, type=t, required=req) for name, t, req in pairs}
    return TypedSchema(id="test", fields=fields)


def test_valid_data_no_errors():
    schema = _schema(("name", StringType(), True), ("age", NumberType(), False))
    errors = check(schema, {"name": "Alice", "age": 30})
    assert errors == []


def test_missing_required_field():
    schema = _schema(("name", StringType(), True), ("email", StringType(), True))
    errors = check(schema, {"name": "Alice"})
    assert len(errors) == 1
    assert errors[0].code == "missing_required_field"
    assert errors[0].field == "email"


def test_type_mismatch_number():
    schema = _schema(("age", NumberType(), True))
    errors = check(schema, {"age": "thirty"})
    assert len(errors) == 1
    assert errors[0].code == "type_mismatch"
    assert errors[0].field == "age"
    assert "number" in errors[0].message
    assert "string" in errors[0].message


def test_invalid_enum_value():
    schema = _schema(("status", EnumType(values=["active", "inactive"]), True))
    errors = check(schema, {"status": "unknown"})
    assert len(errors) == 1
    assert errors[0].code == "invalid_enum_value"
    assert "unknown" in errors[0].message


def test_unknown_field():
    schema = _schema(("name", StringType(), True))
    errors = check(schema, {"name": "Alice", "extra": "value"})
    codes = {e.code for e in errors}
    assert "unknown_field" in codes
    unknown = next(e for e in errors if e.code == "unknown_field")
    assert unknown.field == "extra"


def test_did_you_mean():
    schema = _schema(("name", StringType(), True))
    errors = check(schema, {"naem": "Alice"})
    codes = [e.code for e in errors]
    assert "missing_required_field" in codes
    assert "did_you_mean" in codes
    dym = next(e for e in errors if e.code == "did_you_mean")
    assert "name" in dym.message


def test_nested_object_type_error():
    inner = ObjectType(fields={"city": TSPField(name="city", type=StringType(), required=True)})
    schema = _schema(("address", inner, True))
    errors = check(schema, {"address": {"city": 42}})
    assert len(errors) == 1
    assert errors[0].code == "type_mismatch"
    assert errors[0].field == "address.city"


def test_list_item_type_mismatch():
    schema = _schema(("tags", ListType(item_type=StringType()), True))
    errors = check(schema, {"tags": ["hello", 42, "world"]})
    assert len(errors) == 1
    assert errors[0].code == "type_mismatch"
    assert errors[0].field == "tags[1]"


def test_errors_collected_not_short_circuited():
    schema = _schema(("a", StringType(), True), ("b", NumberType(), True))
    errors = check(schema, {"a": 1, "b": "x"})
    assert len(errors) == 2
```

- [ ] **Step 2: Red**

```bash
cd tsp-python && .venv/bin/pytest tests/test_type_checker.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError.

- [ ] **Step 3: Create `tsp-python/src/tsp_core/type_checker.py`**

```python
from __future__ import annotations
from .models import (
    TypedSchema, TSPField, TSPFieldType,
    StringType, NumberType, BooleanType, NullType, EnumType, ListType, ObjectType,
    TypeCheckError,
)


def _levenshtein(s1: str, s2: str) -> int:
    m, n = len(s1), len(s2)
    dp = list(range(n + 1))
    for i in range(1, m + 1):
        prev, dp[0] = dp[0], i
        for j in range(1, n + 1):
            temp = dp[j]
            dp[j] = prev if s1[i - 1] == s2[j - 1] else 1 + min(prev, dp[j], dp[j - 1])
            prev = temp
    return dp[n]


def _python_type_name(value: object) -> str:
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if value is None:
        return "null"
    if isinstance(value, list):
        return "list"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def check(schema: TypedSchema, data: dict, _prefix: str = "") -> list[TypeCheckError]:
    errors: list[TypeCheckError] = []

    for field_name, field in schema.fields.items():
        path = f"{_prefix}.{field_name}" if _prefix else field_name
        if field_name not in data:
            if field.required:
                errors.append(TypeCheckError(
                    code="missing_required_field",
                    field=path,
                    message=f"Required field '{path}' is missing",
                ))
        else:
            errors.extend(_check_type(field.type, data[field_name], path, schema, data, _prefix))

    for key in data:
        if key not in schema.fields:
            path = f"{_prefix}.{key}" if _prefix else key
            known = list(schema.fields.keys())
            closest = min(known, key=lambda k: _levenshtein(key, k)) if known else None
            if closest is not None and _levenshtein(key, closest) <= 3:
                errors.append(TypeCheckError(
                    code="did_you_mean",
                    field=path,
                    message=f"Unknown field '{path}'. Did you mean '{closest}'?",
                ))
            else:
                errors.append(TypeCheckError(
                    code="unknown_field",
                    field=path,
                    message=f"Field '{path}' is not defined in schema",
                ))

    return errors


def _check_type(
    field_type: TSPFieldType,
    value: object,
    path: str,
    schema: TypedSchema,
    data: dict,
    prefix: str,
) -> list[TypeCheckError]:
    errors: list[TypeCheckError] = []

    if isinstance(field_type, StringType):
        if not isinstance(value, str):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected string, got {_python_type_name(value)}",
            ))

    elif isinstance(field_type, NumberType):
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected number, got {_python_type_name(value)}",
            ))

    elif isinstance(field_type, BooleanType):
        if not isinstance(value, bool):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected boolean, got {_python_type_name(value)}",
            ))

    elif isinstance(field_type, EnumType):
        if value not in field_type.values:
            errors.append(TypeCheckError(
                code="invalid_enum_value",
                field=path,
                message=f"Field '{path}' value '{value}' not in enum [{', '.join(field_type.values)}]",
            ))

    elif isinstance(field_type, ListType):
        if not isinstance(value, list):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected list, got {_python_type_name(value)}",
            ))
        else:
            for i, item in enumerate(value):
                errors.extend(_check_type(field_type.item_type, item, f"{path}[{i}]", schema, data, prefix))

    elif isinstance(field_type, ObjectType):
        if not isinstance(value, dict):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected object, got {_python_type_name(value)}",
            ))
        else:
            sub_schema = TypedSchema(id="", fields=field_type.fields)
            errors.extend(check(sub_schema, value, _prefix=path))

    return errors
```

- [ ] **Step 4: Green**

```bash
cd tsp-python && .venv/bin/pytest tests/test_type_checker.py -v
```

Expected: 9 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_core/type_checker.py tsp-python/tests/test_type_checker.py
git commit -m "feat: tsp-python tsp_core type checker"
```

---

## Task 6: conform-adapter update → py 16/32

**Files:**
- Modify: `tsp-python/conform-adapter/run.py`

- [ ] **Step 1: Update `run.py`** — add imports and type-checker routing. Replace the imports block and `handle` function:

```python
from tsp_core.schema_parser import parse as schema_parse  # noqa: E402
from tsp_core.type_checker import check as type_check  # noqa: E402
from tsp_core.models import typed_schema_from_dict  # noqa: E402


def handle(fixture_id: str, fixture: dict) -> dict:
    try:
        if fixture_id.startswith("schema-parser"):
            result = schema_parse(fixture["yaml"], fixture.get("id", "unknown"))
            return {"output": result}

        if fixture_id.startswith("type-checker"):
            schema = typed_schema_from_dict(fixture["schema"])
            errors = type_check(schema, fixture["data"])
            return {"output": {"errors": [e.to_dict() for e in errors]}}

        return {"output": None, "error": f"Unhandled fixture: {fixture_id}"}
    except Exception as exc:
        return {"output": None, "error": str(exc)}
```

- [ ] **Step 2: Verify py 16/32**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
node tsp-spec/tsp-conform/dist/cli.js --adapters "py:python3 tsp-python/conform-adapter/run.py" 2>&1 | grep -E "py:"
```

Expected: `py: 16/32`.

- [ ] **Step 3: Commit**

```bash
git add tsp-python/conform-adapter/run.py
git commit -m "feat: tsp-python conform-adapter type-checker routing — py 16/32"
```

---

## Task 7: tsp_core/ir_generator.py + tests

**Files:**
- Create: `tsp-python/src/tsp_core/ir_generator.py`
- Create: `tsp-python/tests/test_ir_generator.py`

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_ir_generator.py`:

```python
from tsp_core.ir_generator import generate
from tsp_core.models import (
    TextNode, ExprNode, IfNode, ForEachNode, Condition,
    TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode, TIRResult,
)


def test_basic_expr():
    ast = [TextNode("Hello "), ExprNode("name"), TextNode("!")]
    result = generate(ast, {"name": "Alice"}, "user", "greeting")
    assert result.nodes[1] == TIRExprNode(field="name", resolved="Alice")


def test_missing_path_resolves_to_null():
    ast = [ExprNode("missing")]
    result = generate(ast, {}, "s", "t")
    assert result.nodes[0] == TIRExprNode(field="missing", resolved=None)


def test_if_true_picks_then_branch():
    ast = [IfNode(
        condition=Condition(op="==", left="status", right="active"),
        then_branch=[TextNode("Active")],
        else_branch=[TextNode("Inactive")],
    )]
    result = generate(ast, {"status": "active"}, "s", "t")
    node = result.nodes[0]
    assert isinstance(node, TIRIfNode)
    assert node.condition is True
    assert node.branch == [TIRTextNode("Active")]


def test_if_false_picks_else_branch():
    ast = [IfNode(
        condition=Condition(op="==", left="status", right="active"),
        then_branch=[TextNode("Active")],
        else_branch=[],
    )]
    result = generate(ast, {"status": "inactive"}, "s", "t")
    node = result.nodes[0]
    assert node.condition is False
    assert node.branch == []


def test_foreach_renders_each_item():
    ast = [ForEachNode(var="tag", iterable="tags", body=[ExprNode("tag")])]
    result = generate(ast, {"tags": ["py", "ts", "java"]}, "s", "t")
    node = result.nodes[0]
    assert isinstance(node, TIRForeachNode)
    assert len(node.items) == 3
    assert node.items[0] == [TIRExprNode(field="tag", resolved="py")]
    assert node.items[1] == [TIRExprNode(field="tag", resolved="ts")]


def test_nested_dotted_path():
    ast = [ExprNode("user.address.city")]
    result = generate(ast, {"user": {"address": {"city": "London"}}}, "s", "t")
    assert result.nodes[0] == TIRExprNode(field="user.address.city", resolved="London")


def test_nested_path_missing_segment_returns_null():
    ast = [ExprNode("user.address.city")]
    result = generate(ast, {"user": {}}, "s", "t")
    assert result.nodes[0].resolved is None


def test_condition_equals():
    ast = [IfNode(
        condition=Condition(op="==", left="score", right="100"),
        then_branch=[TextNode("Perfect")],
        else_branch=[],
    )]
    result = generate(ast, {"score": "100"}, "s", "t")
    assert result.nodes[0].condition is True


def test_provenance():
    result = generate([], {}, "my-schema", "my-template")
    assert result.schema_id == "my-schema"
    assert result.template_id == "my-template"
```

- [ ] **Step 2: Red**

```bash
cd tsp-python && .venv/bin/pytest tests/test_ir_generator.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError.

- [ ] **Step 3: Create `tsp-python/src/tsp_core/ir_generator.py`**

```python
from __future__ import annotations
from typing import Any
from .models import (
    ASTNode, TextNode, ExprNode, IfNode, ForEachNode, Condition,
    TIRNode, TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode, TIRResult,
)


def _resolve(data: dict, path: str) -> Any:
    current: Any = data
    for part in path.split("."):
        if not isinstance(current, dict) or part not in current:
            return None
        current = current[part]
    return current


def _evaluate(condition: Condition, data: dict) -> bool:
    if condition.op == "==":
        return str(_resolve(data, condition.left)) == str(condition.right)
    return False


def generate(
    ast_nodes: list[ASTNode],
    data: dict,
    schema_id: str,
    template_id: str,
) -> TIRResult:
    return TIRResult(
        template_id=template_id,
        schema_id=schema_id,
        nodes=[_node(n, data) for n in ast_nodes],
    )


def _node(node: ASTNode, data: dict) -> TIRNode:
    if isinstance(node, TextNode):
        return TIRTextNode(content=node.content)

    if isinstance(node, ExprNode):
        return TIRExprNode(field=node.field, resolved=_resolve(data, node.field))

    if isinstance(node, IfNode):
        cond = _evaluate(node.condition, data)
        branch = node.then_branch if cond else node.else_branch
        return TIRIfNode(condition=cond, branch=[_node(n, data) for n in branch])

    if isinstance(node, ForEachNode):
        items_val = _resolve(data, node.iterable)
        items: list[Any] = items_val if isinstance(items_val, list) else []
        rendered = []
        for item in items:
            scope = {**data, node.var: item}
            rendered.append([_node(n, scope) for n in node.body])
        return TIRForeachNode(var=node.var, items=rendered)

    raise ValueError(f"Unknown AST node: {node}")
```

- [ ] **Step 4: Green**

```bash
cd tsp-python && .venv/bin/pytest tests/test_ir_generator.py -v
```

Expected: 9 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_core/ir_generator.py tsp-python/tests/test_ir_generator.py
git commit -m "feat: tsp-python tsp_core IR generator"
```

---

## Task 8: conform-adapter update → py 24/32

- [ ] **Step 1: Update `run.py`** — add ir-generator imports and routing:

```python
from tsp_core.schema_parser import parse as schema_parse  # noqa: E402
from tsp_core.type_checker import check as type_check  # noqa: E402
from tsp_core.ir_generator import generate as ir_generate  # noqa: E402
from tsp_core.models import (  # noqa: E402
    typed_schema_from_dict, ast_node_from_dict, tir_result_to_dict,
)


def handle(fixture_id: str, fixture: dict) -> dict:
    try:
        if fixture_id.startswith("schema-parser"):
            result = schema_parse(fixture["yaml"], fixture.get("id", "unknown"))
            return {"output": result}

        if fixture_id.startswith("type-checker"):
            schema = typed_schema_from_dict(fixture["schema"])
            errors = type_check(schema, fixture["data"])
            return {"output": {"errors": [e.to_dict() for e in errors]}}

        if fixture_id.startswith("ir-generator"):
            ast_nodes = [ast_node_from_dict(n) for n in fixture["ast"]]
            result = ir_generate(
                ast_nodes,
                fixture["data"],
                fixture["schema_id"],
                fixture["template_id"],
            )
            return {"output": tir_result_to_dict(result)}

        return {"output": None, "error": f"Unhandled fixture: {fixture_id}"}
    except Exception as exc:
        return {"output": None, "error": str(exc)}
```

- [ ] **Step 2: Verify py 24/32**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
node tsp-spec/tsp-conform/dist/cli.js --adapters "py:python3 tsp-python/conform-adapter/run.py" 2>&1 | grep -E "py:"
```

Expected: `py: 24/32`.

- [ ] **Step 3: Commit**

```bash
git add tsp-python/conform-adapter/run.py
git commit -m "feat: tsp-python conform-adapter ir-generator routing — py 24/32"
```

---

## Task 9: tsp_adapter_html + tsp_adapter_yaml + tests

**Files:**
- Create: `tsp-python/src/tsp_adapter_html/html_adapter.py`
- Create: `tsp-python/src/tsp_adapter_yaml/yaml_adapter.py`
- Create: `tsp-python/tests/test_adapters.py`

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_adapters.py`:

```python
from tsp_adapter_html.html_adapter import render as html_render
from tsp_adapter_yaml.yaml_adapter import render as yaml_render
from tsp_core.models import (
    TIRResult, TIRTextNode, TIRExprNode, TIRForeachNode,
)


def _tir(*nodes, template_id="t", schema_id="s"):
    return TIRResult(template_id=template_id, schema_id=schema_id, nodes=list(nodes))


def test_html_basic():
    tir = _tir(TIRTextNode("Hello "), TIRExprNode("name", "Alice"), TIRTextNode("!"),
               template_id="greeting", schema_id="user")
    assert html_render(tir) == "<!-- tsp template_id=greeting schema_id=user -->\nHello Alice!"


def test_html_escapes_special_chars():
    tir = _tir(TIRExprNode("content", "<b>Hello & World</b>"),
               template_id="escape", schema_id="data")
    assert "&lt;b&gt;Hello &amp; World&lt;/b&gt;" in html_render(tir)


def test_html_does_not_escape_text_nodes():
    tir = _tir(TIRTextNode("<li>item</li>"), template_id="t", schema_id="s")
    assert "<li>item</li>" in html_render(tir)


def test_html_provenance_comment():
    tir = _tir(TIRTextNode("Hello"), template_id="my-template", schema_id="my-schema")
    assert html_render(tir).startswith("<!-- tsp template_id=my-template schema_id=my-schema -->")


def test_html_falsy_zero_renders_as_string():
    tir = _tir(TIRTextNode("Count: "), TIRExprNode("count", 0),
               template_id="counter", schema_id="stats")
    assert "Count: 0" in html_render(tir)


def test_html_null_resolves_to_empty():
    tir = _tir(TIRTextNode("X="), TIRExprNode("x", None))
    result = html_render(tir)
    assert "X=" in result
    assert "None" not in result


def test_html_foreach():
    items = [
        [TIRTextNode("<li>"), TIRExprNode("item", "apple"), TIRTextNode("</li>")],
        [TIRTextNode("<li>"), TIRExprNode("item", "banana"), TIRTextNode("</li>")],
    ]
    tir = _tir(TIRForeachNode(var="item", items=items),
               template_id="list", schema_id="data")
    assert "<li>apple</li><li>banana</li>" in html_render(tir)


def test_yaml_basic():
    tir = _tir(TIRTextNode("name: "), TIRExprNode("name", "Alice"),
               template_id="greeting", schema_id="user")
    assert yaml_render(tir) == "# tsp template_id=greeting schema_id=user\nname: Alice"


def test_yaml_does_not_escape():
    tir = _tir(TIRExprNode("content", "<b>Hello</b>"),
               template_id="t", schema_id="s")
    assert "<b>Hello</b>" in yaml_render(tir)


def test_yaml_provenance_comment():
    tir = _tir(TIRTextNode("Hello"), template_id="my-template", schema_id="my-schema")
    assert yaml_render(tir).startswith("# tsp template_id=my-template schema_id=my-schema")
```

- [ ] **Step 2: Red**

```bash
cd tsp-python && .venv/bin/pytest tests/test_adapters.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError.

- [ ] **Step 3: Create `tsp-python/src/tsp_adapter_html/html_adapter.py`**

```python
from __future__ import annotations
import html as _html_module
from tsp_core.models import TIRResult, TIRNode, TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode


def render(tir: TIRResult) -> str:
    header = f"<!-- tsp template_id={tir.template_id} schema_id={tir.schema_id} -->"
    body = "".join(_node(n) for n in tir.nodes)
    return header + "\n" + body


def _node(node: TIRNode) -> str:
    if isinstance(node, TIRTextNode):
        return node.content
    if isinstance(node, TIRExprNode):
        if node.resolved is None:
            return ""
        return _html_module.escape(str(node.resolved))
    if isinstance(node, TIRIfNode):
        return "".join(_node(n) for n in node.branch)
    if isinstance(node, TIRForeachNode):
        return "".join("".join(_node(n) for n in item) for item in node.items)
    return ""
```

- [ ] **Step 4: Create `tsp-python/src/tsp_adapter_yaml/yaml_adapter.py`**

```python
from __future__ import annotations
from tsp_core.models import TIRResult, TIRNode, TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode


def render(tir: TIRResult) -> str:
    header = f"# tsp template_id={tir.template_id} schema_id={tir.schema_id}"
    body = "".join(_node(n) for n in tir.nodes)
    return header + "\n" + body


def _node(node: TIRNode) -> str:
    if isinstance(node, TIRTextNode):
        return node.content
    if isinstance(node, TIRExprNode):
        if node.resolved is None:
            return ""
        return str(node.resolved)
    if isinstance(node, TIRIfNode):
        return "".join(_node(n) for n in node.branch)
    if isinstance(node, TIRForeachNode):
        return "".join("".join(_node(n) for n in item) for item in node.items)
    return ""
```

- [ ] **Step 5: Green**

```bash
cd tsp-python && .venv/bin/pytest tests/test_adapters.py -v
```

Expected: 10 passed.

- [ ] **Step 6: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_adapter_html/ tsp-python/src/tsp_adapter_yaml/ tsp-python/tests/test_adapters.py
git commit -m "feat: tsp-python HTML + YAML adapters"
```

---

## Task 10: conform-adapter final → py 32/32

- [ ] **Step 1: Update `run.py`** — add adapter imports and routing. Full file:

```python
#!/usr/bin/env python3
"""tsp-python adapter — routes fixture IDs to tsp-python handlers."""
from pathlib import Path
import sys
import site
import json

_repo = Path(__file__).resolve().parent.parent
for _sp in (_repo / ".venv" / "lib").glob("python*/site-packages"):
    site.addsitedir(str(_sp))

from tsp_core.schema_parser import parse as schema_parse  # noqa: E402
from tsp_core.type_checker import check as type_check  # noqa: E402
from tsp_core.ir_generator import generate as ir_generate  # noqa: E402
from tsp_adapter_html.html_adapter import render as html_render  # noqa: E402
from tsp_adapter_yaml.yaml_adapter import render as yaml_render  # noqa: E402
from tsp_core.models import (  # noqa: E402
    typed_schema_from_dict, ast_node_from_dict,
    tir_result_from_dict, tir_result_to_dict,
)


def handle(fixture_id: str, fixture: dict) -> dict:
    try:
        if fixture_id.startswith("schema-parser"):
            result = schema_parse(fixture["yaml"], fixture.get("id", "unknown"))
            return {"output": result}

        if fixture_id.startswith("type-checker"):
            schema = typed_schema_from_dict(fixture["schema"])
            errors = type_check(schema, fixture["data"])
            return {"output": {"errors": [e.to_dict() for e in errors]}}

        if fixture_id.startswith("ir-generator"):
            ast_nodes = [ast_node_from_dict(n) for n in fixture["ast"]]
            result = ir_generate(
                ast_nodes,
                fixture["data"],
                fixture["schema_id"],
                fixture["template_id"],
            )
            return {"output": tir_result_to_dict(result)}

        if fixture_id.startswith("adapters/html"):
            tir = tir_result_from_dict(fixture["tir"])
            return {"output": {"output": html_render(tir)}}

        if fixture_id.startswith("adapters/yaml"):
            tir = tir_result_from_dict(fixture["tir"])
            return {"output": {"output": yaml_render(tir)}}

        return {"output": None, "error": f"Unhandled fixture: {fixture_id}"}
    except Exception as exc:
        return {"output": None, "error": str(exc)}


def main() -> None:
    for raw in sys.stdin:
        raw = raw.strip()
        if not raw:
            continue
        req = json.loads(raw)
        result = handle(req["fixture_id"], req["fixture"])
        print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run all tests**

```bash
cd tsp-python && .venv/bin/pytest -v 2>&1 | tail -10
```

Expected: 42 passed (7 models + 7 schema-parser + 9 type-checker + 9 ir-generator + 10 adapters).

- [ ] **Step 3: Run tsp-conform — expect py 32/32**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
node tsp-spec/tsp-conform/dist/cli.js --adapters "py:python3 tsp-python/conform-adapter/run.py" 2>&1 | tail -5
```

Expected: `py: 32/32`.

- [ ] **Step 4: Cross-implementation verification**

```bash
node tsp-spec/tsp-conform/dist/cli.js \
  --adapters \
    "spec:python3 tsp-spec/conform-adapter/run.py" \
    "ts:node tsp-ts/dist/conform-adapter.js" \
    "py:python3 tsp-python/conform-adapter/run.py" 2>&1 | tail -6
```

Expected: all three show 32/32.

- [ ] **Step 5: Commit**

```bash
git add tsp-python/conform-adapter/run.py
git commit -m "feat: tsp-python conform-adapter full routing — py 32/32"
```

---

## Task 11: tsp_core/breaking_change.py + tests

**Files:**
- Create: `tsp-python/src/tsp_core/breaking_change.py`
- Create: `tsp-python/tests/test_breaking_change.py`

Detects four categories of breaking schema changes:
- `removed_field` — field in old schema, absent in new
- `required_change` — field went from optional to required
- `type_change` — field type changed (any change is breaking)
- `enum_value_removed` — an enum value was removed

Non-breaking: adding optional fields, adding enum values, making required optional.

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_breaking_change.py`:

```python
from tsp_core.breaking_change import detect, BreakingChange
from tsp_core.models import (
    TypedSchema, TSPField, StringType, NumberType,
    EnumType, ObjectType,
)


def _s(*pairs):
    return TypedSchema(
        id="x",
        fields={n: TSPField(name=n, type=t, required=r) for n, t, r in pairs},
    )


def test_removed_field_is_breaking():
    old = _s(("name", StringType(), True), ("email", StringType(), False))
    new = _s(("name", StringType(), True))
    changes = detect(old, new)
    assert len(changes) == 1
    assert changes[0].category == "removed_field"
    assert changes[0].field_path == "email"


def test_added_optional_field_not_breaking():
    old = _s(("name", StringType(), True))
    new = _s(("name", StringType(), True), ("age", NumberType(), False))
    assert detect(old, new) == []


def test_optional_to_required_is_breaking():
    old = _s(("email", StringType(), False))
    new = _s(("email", StringType(), True))
    changes = detect(old, new)
    assert len(changes) == 1
    assert changes[0].category == "required_change"
    assert changes[0].field_path == "email"


def test_required_to_optional_not_breaking():
    old = _s(("email", StringType(), True))
    new = _s(("email", StringType(), False))
    assert detect(old, new) == []


def test_type_change_is_breaking():
    old = _s(("count", StringType(), True))
    new = _s(("count", NumberType(), True))
    changes = detect(old, new)
    assert len(changes) == 1
    assert changes[0].category == "type_change"
    assert changes[0].field_path == "count"


def test_enum_value_removed_is_breaking():
    old = _s(("status", EnumType(values=["active", "inactive", "pending"]), True))
    new = _s(("status", EnumType(values=["active", "inactive"]), True))
    changes = detect(old, new)
    assert len(changes) == 1
    assert changes[0].category == "enum_value_removed"
    assert changes[0].field_path == "status"
    assert changes[0].old == "pending"


def test_enum_value_added_not_breaking():
    old = _s(("status", EnumType(values=["active"]), True))
    new = _s(("status", EnumType(values=["active", "inactive"]), True))
    assert detect(old, new) == []


def test_nested_object_recursion():
    inner_old = ObjectType(fields={
        "city": TSPField(name="city", type=StringType(), required=True),
        "zip":  TSPField(name="zip",  type=StringType(), required=False),
    })
    inner_new = ObjectType(fields={
        "city": TSPField(name="city", type=StringType(), required=True),
    })
    old = _s(("address", inner_old, True))
    new = _s(("address", inner_new, True))
    changes = detect(old, new)
    assert len(changes) == 1
    assert changes[0].category == "removed_field"
    assert changes[0].field_path == "address.zip"
```

- [ ] **Step 2: Red**

```bash
cd tsp-python && .venv/bin/pytest tests/test_breaking_change.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError.

- [ ] **Step 3: Create `tsp-python/src/tsp_core/breaking_change.py`**

```python
from __future__ import annotations
from dataclasses import dataclass
from .models import (
    TypedSchema, TSPField,
    EnumType, ObjectType,
)


@dataclass(eq=True)
class BreakingChange:
    category: str   # "removed_field" | "required_change" | "type_change" | "enum_value_removed"
    field_path: str
    old: str
    new: str


def detect(old_schema: TypedSchema, new_schema: TypedSchema) -> list[BreakingChange]:
    return _detect_fields(old_schema.fields, new_schema.fields, prefix="")


def _detect_fields(
    old_fields: dict[str, TSPField],
    new_fields: dict[str, TSPField],
    prefix: str,
) -> list[BreakingChange]:
    changes: list[BreakingChange] = []

    for name, old_field in old_fields.items():
        path = f"{prefix}.{name}" if prefix else name

        if name not in new_fields:
            changes.append(BreakingChange(
                category="removed_field",
                field_path=path,
                old=old_field.type.__class__.__name__,
                new="<absent>",
            ))
            continue

        new_field = new_fields[name]

        if not old_field.required and new_field.required:
            changes.append(BreakingChange(
                category="required_change",
                field_path=path,
                old="optional",
                new="required",
            ))

        if type(old_field.type) is not type(new_field.type):
            changes.append(BreakingChange(
                category="type_change",
                field_path=path,
                old=old_field.type.__class__.__name__,
                new=new_field.type.__class__.__name__,
            ))
            continue

        if isinstance(old_field.type, EnumType) and isinstance(new_field.type, EnumType):
            new_values = set(new_field.type.values)
            for v in old_field.type.values:
                if v not in new_values:
                    changes.append(BreakingChange(
                        category="enum_value_removed",
                        field_path=path,
                        old=v,
                        new="<removed>",
                    ))

        if isinstance(old_field.type, ObjectType) and isinstance(new_field.type, ObjectType):
            changes.extend(_detect_fields(old_field.type.fields, new_field.type.fields, prefix=path))

    return changes
```

- [ ] **Step 4: Green**

```bash
cd tsp-python && .venv/bin/pytest tests/test_breaking_change.py -v
```

Expected: 8 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_core/breaking_change.py tsp-python/tests/test_breaking_change.py
git commit -m "feat: tsp-python breaking change detector"
```

---

## Task 12: tsp_core/hash.py + tests

**Files:**
- Create: `tsp-python/src/tsp_core/hash.py`
- Create: `tsp-python/tests/test_hash.py`

SHA-256 hash of a schema's canonical JSON serialization — used for schema versioning and cache invalidation.

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_hash.py`:

```python
from tsp_core.hash import schema_hash
from tsp_core.models import TypedSchema, TSPField, StringType, NumberType


def test_hash_is_deterministic():
    s1 = TypedSchema(id="x", fields={"name": TSPField(name="name", type=StringType(), required=True)})
    s2 = TypedSchema(id="x", fields={"name": TSPField(name="name", type=StringType(), required=True)})
    assert schema_hash(s1) == schema_hash(s2)


def test_hash_differs_for_different_schemas():
    s1 = TypedSchema(id="x", fields={"name": TSPField(name="name", type=StringType(), required=True)})
    s2 = TypedSchema(id="x", fields={"name": TSPField(name="name", type=NumberType(), required=True)})
    assert schema_hash(s1) != schema_hash(s2)


def test_hash_is_sha256_hex():
    s = TypedSchema(id="x", fields={"name": TSPField(name="name", type=StringType(), required=True)})
    h = schema_hash(s)
    assert isinstance(h, str)
    assert len(h) == 64
    assert all(c in "0123456789abcdef" for c in h)
```

- [ ] **Step 2: Red**

```bash
cd tsp-python && .venv/bin/pytest tests/test_hash.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError.

- [ ] **Step 3: Create `tsp-python/src/tsp_core/hash.py`**

```python
from __future__ import annotations
import hashlib
import json
from .models import TypedSchema, typed_schema_to_dict


def schema_hash(schema: TypedSchema) -> str:
    """SHA-256 hex digest of the schema's canonical JSON serialization.

    Field-order invariant: uses sort_keys=True so {a,b} and {b,a} hash identically.
    """
    data = typed_schema_to_dict(schema)
    serialized = json.dumps(data, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(serialized.encode("utf-8")).hexdigest()
```

- [ ] **Step 4: Green**

```bash
cd tsp-python && .venv/bin/pytest tests/test_hash.py -v
```

Expected: 3 passed.

- [ ] **Step 5: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/tsp_core/hash.py tsp-python/tests/test_hash.py
git commit -m "feat: tsp-python schema content hash (SHA-256)"
```

---

## Task 13: jinja_tsp/environment.py + tests

**Files:**
- Create: `tsp-python/src/jinja_tsp/environment.py`
- Create: `tsp-python/tests/test_jinja_tsp.py`

Wraps `jinja2.Environment`. A template file is a YAML schema followed by `---` separator followed by a Jinja2 body. `get_template()` parses the schema and compiles the body; `template.render(**data)` type-checks data against the schema before rendering.

- [ ] **Step 1: Write failing tests**

Create `tsp-python/tests/test_jinja_tsp.py`:

```python
import tempfile
from pathlib import Path
import pytest
from jinja_tsp.environment import TSPEnvironment, TSPTemplateError


@pytest.fixture
def tmp_templates():
    with tempfile.TemporaryDirectory() as d:
        yield Path(d)


def _write(dir_: Path, name: str, content: str) -> None:
    (dir_ / name).write_text(content)


def test_load_and_render_basic(tmp_templates):
    _write(tmp_templates, "greet.tsp", "name:\n  type: string\n  required: true\n---\nHello {{ name }}!")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("greet.tsp")
    assert tmpl.render(name="Alice") == "Hello Alice!"


def test_render_raises_on_type_error(tmp_templates):
    _write(tmp_templates, "age.tsp", "age:\n  type: number\n  required: true\n---\n{{ age }}")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("age.tsp")
    with pytest.raises(TSPTemplateError):
        tmpl.render(age="old")


def test_render_raises_on_missing_required_field(tmp_templates):
    _write(tmp_templates, "r.tsp", "name:\n  type: string\n  required: true\n---\n{{ name }}")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("r.tsp")
    with pytest.raises(TSPTemplateError):
        tmpl.render()


def test_schema_exposed(tmp_templates):
    _write(tmp_templates, "s.tsp", "name:\n  type: string\n  required: true\n---\nHi")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("s.tsp")
    assert tmpl.schema.id == "s.tsp"
    assert tmpl.schema.fields["name"].required is True


def test_jinja_loop_works(tmp_templates):
    content = (
        "items:\n"
        "  type: list\n"
        "  items:\n"
        "    type: string\n"
        "  required: true\n"
        "---\n"
        "{% for item in items %}- {{ item }}\n{% endfor %}"
    )
    _write(tmp_templates, "loop.tsp", content)
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("loop.tsp")
    assert tmpl.render(items=["a", "b"]) == "- a\n- b\n"


def test_missing_body_raises_on_load(tmp_templates):
    _write(tmp_templates, "nobody.tsp", "name:\n  type: string\n  required: true\n")
    env = TSPEnvironment(tmp_templates)
    with pytest.raises(ValueError):
        env.get_template("nobody.tsp")
```

- [ ] **Step 2: Red**

```bash
cd tsp-python && .venv/bin/pytest tests/test_jinja_tsp.py -v 2>&1 | tail -5
```

Expected: ModuleNotFoundError.

- [ ] **Step 3: Create `tsp-python/src/jinja_tsp/environment.py`**

```python
from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import jinja2
from tsp_core.schema_parser import parse as parse_schema
from tsp_core.type_checker import check as type_check
from tsp_core.models import TypedSchema, typed_schema_from_dict, TypeCheckError


class TSPTemplateError(Exception):
    def __init__(self, errors: list[TypeCheckError]):
        self.errors = errors
        super().__init__("\n".join(f"[{e.code}] {e.message}" for e in errors))


@dataclass
class TSPTemplate:
    name: str
    schema: TypedSchema
    _jinja_template: jinja2.Template

    def render(self, **data) -> str:
        errors = type_check(self.schema, data)
        if errors:
            raise TSPTemplateError(errors)
        return self._jinja_template.render(**data)


class TSPEnvironment:
    def __init__(self, search_path: str | Path):
        self._search_path = Path(search_path)
        self._jinja_env = jinja2.Environment(
            loader=jinja2.FileSystemLoader(str(self._search_path)),
            autoescape=False,
            keep_trailing_newline=True,
        )

    def get_template(self, name: str) -> TSPTemplate:
        source_path = self._search_path / name
        source = source_path.read_text()
        parse_result = parse_schema(source, name)

        if "error" in parse_result:
            raise ValueError(f"Schema parse error in {name}: {parse_result['error']}")
        if "body" not in parse_result:
            raise ValueError(f"Template {name} has no body separator '---'")

        schema = typed_schema_from_dict(parse_result["schema"])
        body = parse_result["body"]
        jinja_template = self._jinja_env.from_string(body)
        return TSPTemplate(name=name, schema=schema, _jinja_template=jinja_template)
```

- [ ] **Step 4: Green**

```bash
cd tsp-python && .venv/bin/pytest tests/test_jinja_tsp.py -v
```

Expected: 6 passed.

- [ ] **Step 5: Run full suite**

```bash
cd tsp-python && .venv/bin/pytest -v 2>&1 | tail -10
```

Expected: 59 passed total (42 core + 8 breaking_change + 3 hash + 6 jinja_tsp).

- [ ] **Step 6: Commit**

```bash
cd /Users/ereshgorantla/Documents/Dev/oss/tsp
git add tsp-python/src/jinja_tsp/environment.py tsp-python/tests/test_jinja_tsp.py
git commit -m "feat: tsp-python jinja_tsp integration (type check on render)"
```

---

## Summary

| Task | Deliverable | Milestone |
|------|-------------|-----------|
| 1 | Scaffold | pyproject + dirs + venv |
| 2 | `tsp_core/models.py` | 7 tests |
| 3 | `tsp_core/schema_parser.py` | 7 tests |
| 4 | `conform-adapter/run.py` (partial) | **py 8/32** ✓ |
| 5 | `tsp_core/type_checker.py` | 9 tests |
| 6 | adapter update | **py 16/32** ✓ |
| 7 | `tsp_core/ir_generator.py` | 9 tests |
| 8 | adapter update | **py 24/32** ✓ |
| 9 | `tsp_adapter_html` + `tsp_adapter_yaml` | 10 tests |
| 10 | adapter final | **py 32/32** ✓ (total: 42 tests) |
| 11 | `tsp_core/breaking_change.py` | 8 tests |
| 12 | `tsp_core/hash.py` | 3 tests |
| 13 | `jinja_tsp/environment.py` | 6 tests |

**Final totals:** 59 unit tests, `py 32/32` on tsp-conform, 3 new feature modules beyond the core reference implementation.
