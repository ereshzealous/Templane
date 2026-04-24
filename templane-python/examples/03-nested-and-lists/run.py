"""03 — Nested objects, enums, lists: a realistic order receipt."""
from pathlib import Path
from jinja_templane import TemplaneEnvironment

env = TemplaneEnvironment(Path(__file__).parent)
tmpl = env.get_template("order.templane")

output = tmpl.render(
    customer={
        "name": "Jordan Shah",
        "email": "jordan@example.com",
        "tier": "pro",
    },
    items=[
        {"sku": "BOOK-042", "qty": 2, "price_cents": 1499},
        {"sku": "PEN-003",  "qty": 5, "price_cents": 299},
        {"sku": "MUG-099",  "qty": 1, "price_cents": 1200},
    ],
    total_cents=5993,
)
print(output)
