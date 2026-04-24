"""06 — Sidecar mode: keep your .jinja files, add a schema beside them.

This is the adoption pattern. password_reset.jinja is a plain Jinja2
template — no Templane-specific syntax, readable by any Jinja tool.
password_reset.schema.templane sits next to it and validates the data
before Jinja ever sees it.
"""
from pathlib import Path
from jinja_templane import TemplaneEnvironment, TemplaneTemplateError

env = TemplaneEnvironment(Path(__file__).parent)
tmpl = env.get_template("password_reset.schema.templane")

# --- Good data: renders cleanly ---
print("--- Good data: renders cleanly ---")
print(tmpl.render(
    product_name="Templane",
    user={"name": "Priya", "email": "priya@example.com"},
    reset_url="https://templane.dev/reset?t=abc123",
    expiry_minutes=30,
))

# --- Bad data: type-check refuses before Jinja sees anything ---
print("--- Bad data: type-check refuses ---")
try:
    tmpl.render(
        product_name="Templane",
        user={"name": "Priya"},         # email missing
        reset_url="https://...",
        expiry_minutes="thirty",         # wrong type
    )
except TemplaneTemplateError as exc:
    print(f"render refused: {len(exc.errors)} error(s)\n")
    for err in exc.errors:
        print(f"  [{err.code}] {err.field}: {err.message}")
