from __future__ import annotations
from .models import (
    TypedSchema, TemplaneField, TemplaneFieldType,
    StringType, NumberType, BooleanType, NullType, EnumType, ListType, ObjectType,
    TypeCheckError,
)


def _levenshtein(s1: str, s2: str) -> int:
    m, n = len(s1), len(s2)
    dp = list(range(n + 1))
    for i in range(1, m + 1):
        prev, dp[0] = dp[0], i
        for j in range(1, n + 1):
            temp = dp[j]
            dp[j] = prev if s1[i - 1] == s2[j - 1] else 1 + min(prev, dp[j], dp[j - 1])
            prev = temp
    return dp[n]


def _python_type_name(value: object) -> str:
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, (int, float)):
        return "number"
    if isinstance(value, str):
        return "string"
    if value is None:
        return "null"
    if isinstance(value, list):
        return "list"
    if isinstance(value, dict):
        return "object"
    return type(value).__name__


def check(schema: TypedSchema, data: dict, _prefix: str = "") -> list[TypeCheckError]:
    errors: list[TypeCheckError] = []

    for field_name, field in schema.fields.items():
        path = f"{_prefix}.{field_name}" if _prefix else field_name
        if field_name not in data:
            if field.required:
                errors.append(TypeCheckError(
                    code="missing_required_field",
                    field=path,
                    message=f"Required field '{path}' is missing",
                ))
        else:
            errors.extend(_check_type(field.type, data[field_name], path, schema, data, _prefix))

    for key in data:
        if key not in schema.fields:
            path = f"{_prefix}.{key}" if _prefix else key
            known = list(schema.fields.keys())
            closest = min(known, key=lambda k: _levenshtein(key, k)) if known else None
            if closest is not None and _levenshtein(key, closest) <= 3:
                errors.append(TypeCheckError(
                    code="did_you_mean",
                    field=path,
                    message=f"Unknown field '{path}'. Did you mean '{closest}'?",
                ))
            else:
                errors.append(TypeCheckError(
                    code="unknown_field",
                    field=path,
                    message=f"Field '{path}' is not defined in schema",
                ))

    return errors


def _check_type(
    field_type: TemplaneFieldType,
    value: object,
    path: str,
    schema: TypedSchema,
    data: dict,
    prefix: str,
) -> list[TypeCheckError]:
    errors: list[TypeCheckError] = []

    if isinstance(field_type, StringType):
        if not isinstance(value, str):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected string, got {_python_type_name(value)}",
            ))

    elif isinstance(field_type, NumberType):
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected number, got {_python_type_name(value)}",
            ))

    elif isinstance(field_type, BooleanType):
        if not isinstance(value, bool):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected boolean, got {_python_type_name(value)}",
            ))

    elif isinstance(field_type, EnumType):
        if value not in field_type.values:
            errors.append(TypeCheckError(
                code="invalid_enum_value",
                field=path,
                message=f"Field '{path}' value '{value}' not in enum [{', '.join(field_type.values)}]",
            ))

    elif isinstance(field_type, ListType):
        if not isinstance(value, list):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected list, got {_python_type_name(value)}",
            ))
        else:
            for i, item in enumerate(value):
                errors.extend(_check_type(field_type.item_type, item, f"{path}[{i}]", schema, data, prefix))

    elif isinstance(field_type, ObjectType):
        if not isinstance(value, dict):
            errors.append(TypeCheckError(
                code="type_mismatch",
                field=path,
                message=f"Field '{path}' expected object, got {_python_type_name(value)}",
            ))
        else:
            sub_schema = TypedSchema(id="", fields=field_type.fields)
            errors.extend(check(sub_schema, value, _prefix=path))

    return errors
