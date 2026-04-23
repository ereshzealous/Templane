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
