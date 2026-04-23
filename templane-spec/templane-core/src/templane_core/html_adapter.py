from __future__ import annotations
import html as _html_module
from .models import TIRResult, TIRNode, TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode


def render(tir: TIRResult) -> str:
    header = f"<!-- templane template_id={tir.template_id} schema_id={tir.schema_id} -->"
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
