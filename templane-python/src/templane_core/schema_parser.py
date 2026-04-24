from __future__ import annotations
import os
from pathlib import Path
import yaml
from .models import (
    TemplaneField, TemplaneFieldType, TypedSchema,
    StringType, NumberType, BooleanType, NullType, EnumType, ListType, ObjectType,
    typed_schema_to_dict,
)

# Top-level keys that are reserved by the protocol (not treated as field names).
_RESERVED_KEYS = {"body", "engine"}

# Extension → engine inference (SPEC §4.3).
_ENGINE_BY_EXT = {
    ".jinja":      "jinja",
    ".jinja2":     "jinja",
    ".j2":         "jinja",
    ".hbs":        "handlebars",
    ".handlebars": "handlebars",
    ".ftl":        "freemarker",
    ".ftlh":       "freemarker",
    ".tmpl":       "gotemplate",
    ".gotmpl":     "gotemplate",
    ".md":         "markdown",
    ".markdown":   "markdown",
    ".html":       "html-raw",
    ".htm":        "html-raw",
    ".yaml":       "yaml-raw",
    ".yml":        "yaml-raw",
}

_VALID_ENGINES = {"jinja", "handlebars", "freemarker", "gotemplate", "markdown", "html-raw", "yaml-raw"}


def parse(yaml_str: str, schema_id: str) -> dict:
    """Parse a Templane schema document.

    Returns a dict with keys:
      - schema: typed schema dict (always present on success)
      - body:    inlined body string (present only for embedded mode)
      - body_path: relative sidecar path (present only for sidecar mode)
      - engine:  declared or inferred engine (present if known)
      - error:   error string (present only on failure)
    """
    body: str | None = None
    has_separator = "\n---\n" in yaml_str

    if has_separator:
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

    # Extract reserved keys before building fields.
    body_path = data.pop("body", None) if isinstance(data.get("body", None), str) or data.get("body", None) is None else None
    engine = data.pop("engine", None) if isinstance(data.get("engine", None), str) or data.get("engine", None) is None else None
    # Re-pop even if type check filtered (keep dict clean).
    data.pop("body", None)
    data.pop("engine", None)

    if body_path is not None and has_separator:
        return {"error": "cannot use both 'body:' key and '---' separator"}

    if body_path is not None:
        if body_path.startswith("/") or ".." in Path(body_path).parts:
            return {"error": "body path must be relative and inside the schema's directory"}

    if engine is not None and engine not in _VALID_ENGINES:
        return {"error": f"unknown engine '{engine}' — must be one of {sorted(_VALID_ENGINES)}"}

    # Infer engine from body path extension if not explicit.
    if engine is None and body_path is not None:
        inferred = _ENGINE_BY_EXT.get(Path(body_path).suffix.lower())
        if inferred is not None:
            engine = inferred

    # Build fields from remaining top-level keys.
    fields: dict[str, TemplaneField] = {}
    for name, field_def in data.items():
        fields[name] = _parse_field(name, field_def or {})

    schema = TypedSchema(id=schema_id, fields=fields)
    result: dict = {"schema": typed_schema_to_dict(schema)}
    if body is not None:
        result["body"] = body
    if body_path is not None:
        result["body_path"] = body_path
    if engine is not None:
        result["engine"] = engine
    return result


def load_from_path(schema_path: str | Path) -> dict:
    """Load a schema from a filesystem path, resolving sidecar body references.

    Returns the same shape as parse(), plus:
      - body (resolved from body_path if sidecar mode)

    On sidecar resolution failure, returns {"error": "..."}.
    """
    schema_path = Path(schema_path)
    try:
        yaml_str = schema_path.read_text()
    except OSError as exc:
        return {"error": f"cannot read schema file: {exc}"}

    result = parse(yaml_str, schema_path.name)
    if "error" in result:
        return result

    if "body_path" in result and "body" not in result:
        body_abs = (schema_path.parent / result["body_path"]).resolve()
        try:
            result["body"] = body_abs.read_text()
        except OSError:
            return {"error": f"body file not found: {result['body_path']}"}

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
