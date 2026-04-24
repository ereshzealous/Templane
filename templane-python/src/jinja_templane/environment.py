from __future__ import annotations
from dataclasses import dataclass
from pathlib import Path
import jinja2
from templane_core.schema_parser import load_from_path
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
        """Load a Templane schema (embedded or sidecar) and compile its body.

        `name` is resolved against the environment's search_path. If the
        schema uses sidecar mode (`body: ./path.jinja`), the body file is
        resolved relative to the schema's directory.
        """
        schema_path = self._search_path / name
        result = load_from_path(schema_path)

        if "error" in result:
            raise ValueError(f"Schema load error in {name}: {result['error']}")
        if "body" not in result:
            raise ValueError(
                f"Template {name} has no renderable body — "
                "add a '---' separator or a 'body:' key pointing to an external file"
            )

        schema = typed_schema_from_dict(result["schema"])
        jinja_template = self._jinja_env.from_string(result["body"])
        return TemplaneTemplate(name=name, schema=schema, _jinja_template=jinja_template)
