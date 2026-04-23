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
