import tempfile
from pathlib import Path
import pytest
from jinja_tsp.environment import TSPEnvironment, TSPTemplateError


@pytest.fixture
def tmp_templates():
    with tempfile.TemporaryDirectory() as d:
        yield Path(d)


def _write(dir_: Path, name: str, content: str) -> None:
    (dir_ / name).write_text(content)


def test_load_and_render_basic(tmp_templates):
    _write(tmp_templates, "greet.tsp", "name:\n  type: string\n  required: true\n---\nHello {{ name }}!")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("greet.tsp")
    assert tmpl.render(name="Alice") == "Hello Alice!"


def test_render_raises_on_type_error(tmp_templates):
    _write(tmp_templates, "age.tsp", "age:\n  type: number\n  required: true\n---\n{{ age }}")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("age.tsp")
    with pytest.raises(TSPTemplateError):
        tmpl.render(age="old")


def test_render_raises_on_missing_required_field(tmp_templates):
    _write(tmp_templates, "r.tsp", "name:\n  type: string\n  required: true\n---\n{{ name }}")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("r.tsp")
    with pytest.raises(TSPTemplateError):
        tmpl.render()


def test_schema_exposed(tmp_templates):
    _write(tmp_templates, "s.tsp", "name:\n  type: string\n  required: true\n---\nHi")
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("s.tsp")
    assert tmpl.schema.id == "s.tsp"
    assert tmpl.schema.fields["name"].required is True


def test_jinja_loop_works(tmp_templates):
    content = (
        "items:\n"
        "  type: list\n"
        "  items:\n"
        "    type: string\n"
        "  required: true\n"
        "---\n"
        "{% for item in items %}- {{ item }}\n{% endfor %}"
    )
    _write(tmp_templates, "loop.tsp", content)
    env = TSPEnvironment(tmp_templates)
    tmpl = env.get_template("loop.tsp")
    assert tmpl.render(items=["a", "b"]) == "- a\n- b\n"


def test_missing_body_raises_on_load(tmp_templates):
    _write(tmp_templates, "nobody.tsp", "name:\n  type: string\n  required: true\n")
    env = TSPEnvironment(tmp_templates)
    with pytest.raises(ValueError):
        env.get_template("nobody.tsp")
