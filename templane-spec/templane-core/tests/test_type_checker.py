from templane_core.type_checker import check
from templane_core.models import (
    TypedSchema, TemplaneField, StringType, NumberType, BooleanType,
    EnumType, ListType, ObjectType,
)


def _schema(*pairs):
    fields = {name: TemplaneField(name=name, type=t, required=req) for name, t, req in pairs}
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
    inner = ObjectType(fields={"city": TemplaneField(name="city", type=StringType(), required=True)})
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
