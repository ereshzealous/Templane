from __future__ import annotations
import yaml
from .models import (
    TemplaneField, TemplaneFieldType, TypedSchema,
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

    fields: dict[str, TemplaneField] = {}
    for name, field_def in data.items():
        fields[name] = _parse_field(name, field_def or {})

    schema = TypedSchema(id=schema_id, fields=fields)
    result: dict = {"schema": typed_schema_to_dict(schema)}
    if body is not None:
        result["body"] = body
    return result


def _parse_field(name: str, field_def: dict) -> TemplaneField:
    type_str = field_def.get("type", "string")
    required = bool(field_def.get("required", False))
    return TemplaneField(name=name, type=_parse_type(type_str, field_def), required=required)


def _parse_type(type_str: str, field_def: dict) -> TemplaneFieldType:
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
        sub_fields: dict[str, TemplaneField] = {}
        for fname, fdef in (field_def.get("fields") or {}).items():
            sub_fields[fname] = _parse_field(fname, fdef or {})
        return ObjectType(fields=sub_fields)
    return StringType()
