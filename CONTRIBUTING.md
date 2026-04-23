# Contributing to Templane

Thanks for your interest in Templane. There are three main ways to contribute:

1. **Extend the protocol specification** (new type, new operation).
2. **Add a new language binding** (e.g. Rust, Ruby, C#).
3. **Add a new template-engine integration** within an existing language
   (e.g. Nunjucks-templane, Pongo2-templane, Liquid-templane).

---

## Ground rules

- Every change **MUST** keep all existing implementations at 32/32 on the
  conformance suite.
- New features **MUST** be defined in [SPEC.md](SPEC.md) before they are
  implemented.
- New features **MUST** include fixtures in `templane-spec/fixtures/` and be
  implemented across **all** existing language bindings before merging.
  (Protocol drift between implementations is the single failure mode Templane is
  designed to prevent.)

---

## Extending the protocol

New types, operations, or breaking-change categories require a spec change.

1. Open an issue describing the feature and its motivation.
2. Draft SPEC.md changes in a PR.
3. Add one or more fixtures demonstrating the feature to
   `templane-spec/fixtures/<category>/<name>.json`.
4. Update all five reference implementations to pass the new fixtures.
5. Bump the specification version (SPEC.md header) if semantically
   breaking; otherwise increment the minor version.

Breaking changes to the protocol itself are discouraged unless they fix a
genuine ambiguity. Use the schema-evolution detector's own categories
(`removed_field`, `type_change`, etc.) as a mental model when evaluating
whether a proposal is breaking.

---

## Adding a new language binding

Templane is designed to be implementable in any language with JSON, YAML, and
subprocess I/O. Rough effort: ~3 hours for someone comfortable with the
target language.

### Workflow

1. Read [SPEC.md](SPEC.md) cover to cover. Pay particular attention to
   §5 (wire format), §7.2 (error message formats), and §9 (conform adapter
   protocol).
2. Set up the package skeleton using your language's idiomatic layout.
3. Implement the four operations (parse, check, generate, render) against
   `templane-spec/fixtures/`. Run `templane-conform` incrementally:
   - 8/32 after schema-parser
   - 16/32 after type-checker
   - 24/32 after ir-generator
   - 32/32 after both adapters
4. Build a conform adapter (subprocess shim). See any of the five existing
   adapters for reference (`templane-spec/conform-adapter/run.py`,
   `templane-ts/src/conform-adapter.ts`, etc.).
5. Wire up `templane-conform.yaml` (root) to register your adapter.
6. Add a README.md for your binding following the same structure as the
   existing ones (installation, quick start, API, tests, conformance).
7. Open a PR linking your binding in the top-level README's implementation
   matrix.

### Design tips

- Error message strings are **verbatim** — they must match character-for-
  character. See SPEC.md §7.2.
- TIR expression nodes **MUST** serialize `"resolved": null` when the value
  is null; don't let your JSON library drop it.
- Field paths use dot notation for objects, bracket for list indices
  (`address.city`, `tags[1]`).
- The type checker **MUST NOT** short-circuit; collect all errors.
- Levenshtein distance ≤ 3 triggers did-you-mean.

### Study the existing implementations

Each existing implementation emphasizes a different idiom:

- **TypeScript** — discriminated unions as native types (no serialization helpers needed).
- **Python (reference)** — dataclasses + to_dict/from_dict helpers.
- **Python (production)** — same core + Jinja2 integration, breaking-change detector, schema hash.
- **Java** — sealed interfaces + records + pattern matching on sealed types.
- **Go** — flat structs with `Kind` discriminator + custom MarshalJSON/UnmarshalJSON.

Pick the idiom closest to your target language.

---

## Adding a template-engine integration

Templane's value is multiplied by every engine integration. Within an existing
language, you can add a new integration without changing the protocol.

Pattern (example: adding Liquid support in Python):

```python
# liquid_templane/environment.py
import liquid
from templane_core.schema_parser import parse as parse_schema
from templane_core.type_checker import check as type_check
from templane_core.models import typed_schema_from_dict

class TemplaneLiquidEnvironment:
    def __init__(self, search_path):
        self._search_path = search_path
        self._liquid_env = liquid.Environment(loader=...)

    def get_template(self, name):
        # Read file, split on \n---\n, parse schema, compile body.
        # Return a wrapper that type-checks on render().
        ...
```

Key requirements:

- Type checking at **render time** (since data is only known then).
- Raise a `TemplaneTemplateError` (or language-equivalent) containing all
  type-check errors — don't short-circuit.
- Expose the parsed `TypedSchema` on the returned template object so callers
  can introspect it.

See `templane-python/src/jinja_templane/environment.py`, `templane-ts/src/handlebars-templane.ts`,
and `templane-java/freemarker-templane/` for reference implementations.

---

## Development workflow

### Running the full conformance matrix

```bash
# Build everything
cd templane-ts && npm install && npm run build
cd ../templane-python && uv sync --extra dev
cd ../templane-java && ./gradlew build
cd ../templane-go && go build ./...
cd ..

# Run
node templane-spec/templane-conform/dist/cli.js \
  --adapters \
    "spec:python3 templane-spec/conform-adapter/run.py" \
    "ts:node templane-ts/dist/conform-adapter.js" \
    "py:python3 templane-python/conform-adapter/run.py" \
    "java:templane-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar" \
    "go:templane-go/bin/conform-adapter"
```

All five must report 32/32.

### Running language-specific tests

```bash
# Python (reference)
cd templane-spec/templane-core && .venv/bin/pytest

# TypeScript
cd templane-ts && npm test

# Python (production)
cd templane-python && .venv/bin/pytest

# Java
cd templane-java && ./gradlew test

# Go
cd templane-go && go test ./...
```

---

## Code of conduct

Be kind. Disagree on technical merit. Avoid ad-hominem. If someone's
contribution doesn't meet the bar, explain *what specifically* is missing
and *why* it matters — don't just say "needs work."

---

## License

By contributing, you agree that your contributions will be licensed under
the Apache License 2.0 (see [LICENSE](LICENSE)).
