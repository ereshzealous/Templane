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
