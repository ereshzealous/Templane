"""Tests for SPEC §4.3 — sidecar mode."""
from pathlib import Path
import pytest
from templane_core.schema_parser import parse, load_from_path


def test_sidecar_body_path_recognized():
    yaml = "body: ./email.jinja\nname:\n  type: string\n  required: true\n"
    result = parse(yaml, "sidecar-basic")
    assert "error" not in result
    assert result["body_path"] == "./email.jinja"
    # body: key must NOT be treated as a field
    assert "body" not in result["schema"]["fields"]
    assert list(result["schema"]["fields"].keys()) == ["name"]


def test_sidecar_engine_explicit():
    yaml = "body: ./t.hbs\nengine: jinja\nuser: {type: string, required: true}\n"
    result = parse(yaml, "engine-explicit")
    # explicit engine wins even when extension disagrees
    assert result["engine"] == "jinja"


def test_sidecar_engine_inferred_from_extension():
    cases = [
        ("./t.jinja",  "jinja"),
        ("./t.hbs",    "handlebars"),
        ("./t.ftl",    "freemarker"),
        ("./t.tmpl",   "gotemplate"),
        ("./t.md",     "markdown"),
        ("./t.html",   "html-raw"),
    ]
    for body_path, expected_engine in cases:
        yaml = f"body: {body_path}\nname: {{type: string, required: true}}\n"
        result = parse(yaml, "inferred")
        assert result["engine"] == expected_engine, f"for {body_path}"


def test_sidecar_no_extension_no_engine_inference():
    yaml = "body: ./t\nname: {type: string, required: true}\n"
    result = parse(yaml, "no-ext")
    assert "engine" not in result  # no extension → no inference, no error (engine optional)


def test_sidecar_unknown_engine_rejected():
    yaml = "body: ./t.jinja\nengine: mystery\nname: {type: string, required: true}\n"
    result = parse(yaml, "bad-engine")
    assert "error" in result
    assert "mystery" in result["error"]


def test_sidecar_absolute_path_rejected():
    yaml = "body: /etc/passwd\nname: {type: string, required: true}\n"
    result = parse(yaml, "abs-path")
    assert "error" in result
    assert "relative" in result["error"].lower()


def test_sidecar_parent_escape_rejected():
    yaml = "body: ../../../etc/passwd\nname: {type: string, required: true}\n"
    result = parse(yaml, "escape")
    assert "error" in result


def test_sidecar_and_separator_conflict_rejected():
    yaml = "body: ./a.jinja\nname: {type: string, required: true}\n---\nHello\n"
    result = parse(yaml, "conflict")
    assert "error" in result
    assert "both" in result["error"].lower()


def test_embedded_mode_unchanged_by_1_1():
    """Backward compat: a 1.0 schema with --- separator still works."""
    yaml = "name:\n  type: string\n  required: true\n---\nHello {{ name }}!\n"
    result = parse(yaml, "embedded")
    assert "error" not in result
    assert "body_path" not in result
    assert "engine" not in result
    assert result["body"] == "Hello {{ name }}!\n"


def test_check_only_mode():
    """Schema with neither body: key nor --- → check-only, no body emitted."""
    yaml = "name:\n  type: string\n  required: true\n"
    result = parse(yaml, "check-only")
    assert "error" not in result
    assert "body" not in result
    assert "body_path" not in result


def test_load_from_path_resolves_sidecar(tmp_path: Path):
    """load_from_path() reads the schema, reads the sidecar body, returns both."""
    body = tmp_path / "greeting.jinja"
    body.write_text("Hello {{ name }}!\n")
    schema = tmp_path / "greeting.templane"
    schema.write_text("body: ./greeting.jinja\nname: {type: string, required: true}\n")

    result = load_from_path(schema)
    assert "error" not in result
    assert result["body_path"] == "./greeting.jinja"
    assert result["body"] == "Hello {{ name }}!\n"
    assert result["engine"] == "jinja"


def test_load_from_path_missing_body_file(tmp_path: Path):
    schema = tmp_path / "broken.templane"
    schema.write_text("body: ./nope.jinja\nname: {type: string, required: true}\n")
    result = load_from_path(schema)
    assert "error" in result
    assert "body file" in result["error"].lower()


def test_load_from_path_embedded_mode(tmp_path: Path):
    """load_from_path on an embedded schema should work the same as parse()."""
    schema = tmp_path / "embedded.templane"
    schema.write_text("name: {type: string, required: true}\n---\nHi {{ name }}\n")
    result = load_from_path(schema)
    assert "error" not in result
    assert result["body"] == "Hi {{ name }}\n"
    assert "body_path" not in result
