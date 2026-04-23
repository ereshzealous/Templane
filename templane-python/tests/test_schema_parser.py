from templane_core.schema_parser import parse


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
