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
