from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import jinja2
from templane_core.schema_parser import parse as parse_schema
from templane_core.type_checker import check as type_check
from templane_core.models import TypedSchema, typed_schema_from_dict, TypeCheckError


class TemplaneTemplateError(Exception):
    def __init__(self, errors: list[TypeCheckError]):
        self.errors = errors
        super().__init__("\n".join(f"[{e.code}] {e.message}" for e in errors))


@dataclass
class TemplaneTemplate:
    name: str
    schema: TypedSchema
    _jinja_template: jinja2.Template

    def render(self, **data) -> str:
        errors = type_check(self.schema, data)
        if errors:
            raise TemplaneTemplateError(errors)
        return self._jinja_template.render(**data)


class TemplaneEnvironment:
    def __init__(self, search_path: str | Path):
        self._search_path = Path(search_path)
        self._jinja_env = jinja2.Environment(
            loader=jinja2.FileSystemLoader(str(self._search_path)),
            autoescape=False,
            keep_trailing_newline=True,
        )

    def get_template(self, name: str) -> TemplaneTemplate:
        source_path = self._search_path / name
        source = source_path.read_text()
        parse_result = parse_schema(source, name)

        if "error" in parse_result:
            raise ValueError(f"Schema parse error in {name}: {parse_result['error']}")
        if "body" not in parse_result:
            raise ValueError(f"Template {name} has no body separator '---'")

        schema = typed_schema_from_dict(parse_result["schema"])
        body = parse_result["body"]
        jinja_template = self._jinja_env.from_string(body)
        return TemplaneTemplate(name=name, schema=schema, _jinja_template=jinja_template)
