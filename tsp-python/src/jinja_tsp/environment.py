from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import jinja2
from tsp_core.schema_parser import parse as parse_schema
from tsp_core.type_checker import check as type_check
from tsp_core.models import TypedSchema, typed_schema_from_dict, TypeCheckError


class TSPTemplateError(Exception):
    def __init__(self, errors: list[TypeCheckError]):
        self.errors = errors
        super().__init__("\n".join(f"[{e.code}] {e.message}" for e in errors))


@dataclass
class TSPTemplate:
    name: str
    schema: TypedSchema
    _jinja_template: jinja2.Template

    def render(self, **data) -> str:
        errors = type_check(self.schema, data)
        if errors:
            raise TSPTemplateError(errors)
        return self._jinja_template.render(**data)


class TSPEnvironment:
    def __init__(self, search_path: str | Path):
        self._search_path = Path(search_path)
        self._jinja_env = jinja2.Environment(
            loader=jinja2.FileSystemLoader(str(self._search_path)),
            autoescape=False,
            keep_trailing_newline=True,
        )

    def get_template(self, name: str) -> TSPTemplate:
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
        return TSPTemplate(name=name, schema=schema, _jinja_template=jinja_template)
