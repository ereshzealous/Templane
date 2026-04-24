import tempfile
from pathlib import Path
import pytest
from jinja_templane.environment import TemplaneEnvironment, TemplaneTemplateError


@pytest.fixture
def tmp_templates():
    with tempfile.TemporaryDirectory() as d:
        yield Path(d)


def _write(dir_: Path, name: str, content: str) -> None:
    (dir_ / name).write_text(content)


def test_load_and_render_basic(tmp_templates):
    _write(tmp_templates, "greet.templane", "name:\n  type: string\n  required: true\n---\nHello {{ name }}!")
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("greet.templane")
    assert tmpl.render(name="Alice") == "Hello Alice!"


def test_render_raises_on_type_error(tmp_templates):
    _write(tmp_templates, "age.templane", "age:\n  type: number\n  required: true\n---\n{{ age }}")
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("age.templane")
    with pytest.raises(TemplaneTemplateError):
        tmpl.render(age="old")


def test_render_raises_on_missing_required_field(tmp_templates):
    _write(tmp_templates, "r.templane", "name:\n  type: string\n  required: true\n---\n{{ name }}")
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("r.templane")
    with pytest.raises(TemplaneTemplateError):
        tmpl.render()


def test_schema_exposed(tmp_templates):
    _write(tmp_templates, "s.templane", "name:\n  type: string\n  required: true\n---\nHi")
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("s.templane")
    assert tmpl.schema.id == "s.templane"
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
    _write(tmp_templates, "loop.templane", content)
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("loop.templane")
    assert tmpl.render(items=["a", "b"]) == "- a\n- b\n"


def test_missing_body_raises_on_load(tmp_templates):
    _write(tmp_templates, "nobody.templane", "name:\n  type: string\n  required: true\n")
    env = TemplaneEnvironment(tmp_templates)
    with pytest.raises(ValueError):
        env.get_template("nobody.templane")


# ---------------------------------------------------------------------------
# Sidecar mode (SPEC 1.1 §4.3) — schema references external body file
# ---------------------------------------------------------------------------

def test_sidecar_loads_external_body(tmp_templates):
    _write(tmp_templates, "email.jinja", "Hi {{ name }}!")
    _write(
        tmp_templates,
        "email.templane",
        "body: ./email.jinja\nname:\n  type: string\n  required: true\n",
    )
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("email.templane")
    assert tmpl.render(name="Lin") == "Hi Lin!"


def test_sidecar_type_check_still_fires(tmp_templates):
    _write(tmp_templates, "age.jinja", "You are {{ age }}")
    _write(
        tmp_templates,
        "age.templane",
        "body: ./age.jinja\nage:\n  type: number\n  required: true\n",
    )
    env = TemplaneEnvironment(tmp_templates)
    tmpl = env.get_template("age.templane")
    with pytest.raises(TemplaneTemplateError) as exc_info:
        tmpl.render(age="forever")
    assert any(e.code == "type_mismatch" for e in exc_info.value.errors)


def test_sidecar_missing_body_file_raises(tmp_templates):
    _write(
        tmp_templates,
        "broken.templane",
        "body: ./not-here.jinja\nname:\n  type: string\n  required: true\n",
    )
    env = TemplaneEnvironment(tmp_templates)
    with pytest.raises(ValueError) as exc_info:
        env.get_template("broken.templane")
    assert "body file" in str(exc_info.value).lower()
