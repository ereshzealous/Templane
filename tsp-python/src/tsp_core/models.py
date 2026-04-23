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
