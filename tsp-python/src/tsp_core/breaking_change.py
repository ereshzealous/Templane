from __future__ import annotations
from dataclasses import dataclass
from .models import (
    TypedSchema, TSPField,
    EnumType, ObjectType,
)


@dataclass(eq=True)
class BreakingChange:
    category: str   # "removed_field" | "required_change" | "type_change" | "enum_value_removed"
    field_path: str
    old: str
    new: str


def detect(old_schema: TypedSchema, new_schema: TypedSchema) -> list[BreakingChange]:
    return _detect_fields(old_schema.fields, new_schema.fields, prefix="")


def _detect_fields(
    old_fields: dict[str, TSPField],
    new_fields: dict[str, TSPField],
    prefix: str,
) -> list[BreakingChange]:
    changes: list[BreakingChange] = []

    for name, old_field in old_fields.items():
        path = f"{prefix}.{name}" if prefix else name

        if name not in new_fields:
            changes.append(BreakingChange(
                category="removed_field",
                field_path=path,
                old=old_field.type.__class__.__name__,
                new="<absent>",
            ))
            continue

        new_field = new_fields[name]

        if not old_field.required and new_field.required:
            changes.append(BreakingChange(
                category="required_change",
                field_path=path,
                old="optional",
                new="required",
            ))

        if type(old_field.type) is not type(new_field.type):
            changes.append(BreakingChange(
                category="type_change",
                field_path=path,
                old=old_field.type.__class__.__name__,
                new=new_field.type.__class__.__name__,
            ))
            continue

        if isinstance(old_field.type, EnumType) and isinstance(new_field.type, EnumType):
            new_values = set(new_field.type.values)
            for v in old_field.type.values:
                if v not in new_values:
                    changes.append(BreakingChange(
                        category="enum_value_removed",
                        field_path=path,
                        old=v,
                        new="<removed>",
                    ))

        if isinstance(old_field.type, ObjectType) and isinstance(new_field.type, ObjectType):
            changes.extend(_detect_fields(old_field.type.fields, new_field.type.fields, prefix=path))

    return changes
