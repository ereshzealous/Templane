from templane_core.models import (
    StringType, NumberType, BooleanType, EnumType, ListType, ObjectType,
    TemplaneField, TypedSchema, TypeCheckError,
    TextNode, ExprNode, Condition, IfNode, ForEachNode,
    TIRTextNode, TIRExprNode, TIRIfNode, TIRForeachNode, TIRResult,
    templane_field_type_to_dict, templane_field_type_from_dict,
    templane_field_to_dict, templane_field_from_dict,
    typed_schema_to_dict, typed_schema_from_dict,
    ast_node_to_dict, ast_node_from_dict,
    tir_node_to_dict, tir_node_from_dict,
    tir_result_to_dict, tir_result_from_dict,
)


def test_string_type_roundtrip():
    t = StringType()
    assert templane_field_type_from_dict(templane_field_type_to_dict(t)) == t


def test_enum_type_roundtrip():
    t = EnumType(values=["a", "b", "c"])
    assert templane_field_type_from_dict(templane_field_type_to_dict(t)) == t


def test_list_type_roundtrip():
    t = ListType(item_type=StringType())
    assert templane_field_type_from_dict(templane_field_type_to_dict(t)) == t


def test_object_type_roundtrip():
    t = ObjectType(fields={"city": TemplaneField(name="city", type=StringType(), required=True)})
    result = templane_field_type_from_dict(templane_field_type_to_dict(t))
    assert result == t


def test_typed_schema_roundtrip():
    schema = TypedSchema(
        id="test",
        fields={"name": TemplaneField(name="name", type=StringType(), required=True)},
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
