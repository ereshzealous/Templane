"""01 — Hello: parse a Templane file, check, and render with Jinja."""
from pathlib import Path
from jinja_templane import TemplaneEnvironment

here = Path(__file__).parent
env = TemplaneEnvironment(here)
tmpl = env.get_template("greeting.templane")

output = tmpl.render(
    name="Arya",
    temperature_c=22,
    is_morning=True,
)
print(output)
