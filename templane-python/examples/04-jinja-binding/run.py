"""04 — Jinja binding: use jinja_templane with real Jinja2 features.

Jinja filters, conditionals, loops — all work, but the render() call
first type-checks the data against the schema. Bad data is refused
before Jinja ever sees it.
"""
from pathlib import Path
from jinja_templane import TemplaneEnvironment

env = TemplaneEnvironment(Path(__file__).parent)
tmpl = env.get_template("email.templane")

output = tmpl.render(
    user={"name": "Lin", "signup_days_ago": 3},
    unread_count=4,
    notifications=[
        {"kind": "mention", "source": "alex"},
        {"kind": "reply",   "source": "Priya"},
        {"kind": "follow",  "source": "design-team"},
        {"kind": "mention", "source": "jamie"},
    ],
)
print(output)
