# templane-spec

The Templane protocol specification hub: **32 conformance fixtures**, the
`templane-conform` CLI (Node.js), and a Python reference implementation (`templane-core`).

All Templane implementations derive correctness from the fixtures in this package.
If `templane-conform` reports `<name> 40/40`, the implementation is compliant.

---

## Contents

```
templane-spec/
├── fixtures/              ← 32 JSON fixture files (input + expected output)
│   ├── schema-parser/     (8)
│   ├── type-checker/      (8)
│   ├── ir-generator/      (8)
│   └── adapters/          (8)
├── templane-conform/           ← Node.js CLI test runner
│   └── src/cli.ts
├── templane-core/              ← Python reference implementation
│   └── src/templane_core/
└── conform-adapter/       ← Python subprocess shim for templane-conform
    └── run.py
```

---

## Running the conformance suite

From the repo root:

```bash
cd templane-spec/templane-conform
npm install && npm run build

# Run the Python reference adapter:
node dist/cli.js --adapters "spec:python3 ../conform-adapter/run.py"
```

Expected:

```
Running 40 fixture(s) across 1 implementation(s)...
  ✓ spec:   40/40
All implementations conformant.
```

To test multiple language bindings at once, pass multiple `--adapters` entries.
See the [root README](../README.md#running-the-conformance-suite).

---

## Fixture format

Every fixture is a single JSON file:

```json
{
  "fixture_id": "schema-parser/basic",
  "input": { ... },
  "expected_output": { ... }
}
```

See [SPEC.md §9.4](../SPEC.md#94-fixture-file-format) for the normative format
and [SPEC.md Appendix B](../SPEC.md#appendix-b--fixture-index) for the fixture
index by category.

---

## Adapter protocol

Conform adapters are subprocess shims that receive fixture inputs on stdin
and emit results on stdout, one JSON per line:

**Request (stdin):**
```json
{"fixture_id": "schema-parser/basic", "fixture": {...}}
```

**Response (stdout):**
```json
{"output": {...}}
```

See [SPEC.md §9](../SPEC.md#9-conform-adapter-protocol) for full transport and
routing details.

---

## Reference implementation (Python)

`templane-core/` contains the Python reference implementation:

```bash
cd templane-spec/templane-core
uv sync --extra dev
.venv/bin/pytest -v
```

Expected: **42 tests passing**.

Module layout:

- `templane_core.models` — dataclasses + to_dict/from_dict
- `templane_core.schema_parser` — YAML → TypedSchema
- `templane_core.type_checker` — validation + Levenshtein did-you-mean
- `templane_core.ir_generator` — AST → TIR
- `templane_core.html_adapter` / `templane_core.yaml_adapter` — TIR → string

---

## Adding a new language binding

1. Read the [specification](../SPEC.md).
2. Implement the four operations (parse, check, generate, render).
3. Write a conform adapter that reads fixtures from stdin, invokes your
   implementation, and writes results to stdout.
4. Run `templane-conform` with `--adapters "<lang>:<command>"` and iterate
   until 40/40.
5. Submit a PR linking your implementation in the top-level README.

The five existing implementations (Python, TypeScript, Java, Go) are
reference examples of this workflow.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
