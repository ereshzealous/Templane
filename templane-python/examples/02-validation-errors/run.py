"""02 — Validation errors: surface every error code in one pass."""
from pathlib import Path
from jinja_templane import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment(Path(__file__).parent)
tmpl = env.get_template("profile.templane")

# Intentionally bad data to show every error kind at once.
bad_data = {
    # "name" missing             → missing_required_field
    "age": "thirty",              # type_mismatch (expected number)
    "role": "superuser",          # invalid_enum_value
    "rol": "admin",               # unknown_field → did_you_mean "role"
}

try:
    tmpl.render(**bad_data)
except TemplaneTemplateError as exc:
    print(f"render refused: {len(exc.errors)} error(s)\n")
    for err in exc.errors:
        print(f"  [{err.code}] {err.field}: {err.message}")
