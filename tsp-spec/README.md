# tsp-spec

The TSP protocol specification hub: **32 conformance fixtures**, the
`tsp-conform` CLI (Node.js), and a Python reference implementation (`tsp-core`).

All TSP implementations derive correctness from the fixtures in this package.
If `tsp-conform` reports `<name> 32/32`, the implementation is compliant.

---

## Contents

```
tsp-spec/
├── fixtures/              ← 32 JSON fixture files (input + expected output)
│   ├── schema-parser/     (8)
│   ├── type-checker/      (8)
│   ├── ir-generator/      (8)
│   └── adapters/          (8)
├── tsp-conform/           ← Node.js CLI test runner
│   └── src/cli.ts
├── tsp-core/              ← Python reference implementation
│   └── src/tsp_core/
└── conform-adapter/       ← Python subprocess shim for tsp-conform
    └── run.py
```

---

## Running the conformance suite

From the repo root:

```bash
cd tsp-spec/tsp-conform
npm install && npm run build

# Run the Python reference adapter:
node dist/cli.js --adapters "spec:python3 ../conform-adapter/run.py"
```

Expected:

```
Running 32 fixture(s) across 1 implementation(s)...
  ✓ spec:   32/32
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

`tsp-core/` contains the Python reference implementation:

```bash
cd tsp-spec/tsp-core
uv sync --extra dev
.venv/bin/pytest -v
```

Expected: **42 tests passing**.

Module layout:

- `tsp_core.models` — dataclasses + to_dict/from_dict
- `tsp_core.schema_parser` — YAML → TypedSchema
- `tsp_core.type_checker` — validation + Levenshtein did-you-mean
- `tsp_core.ir_generator` — AST → TIR
- `tsp_core.html_adapter` / `tsp_core.yaml_adapter` — TIR → string

---

## Adding a new language binding

1. Read the [specification](../SPEC.md).
2. Implement the four operations (parse, check, generate, render).
3. Write a conform adapter that reads fixtures from stdin, invokes your
   implementation, and writes results to stdout.
4. Run `tsp-conform` with `--adapters "<lang>:<command>"` and iterate
   until 32/32.
5. Submit a PR linking your implementation in the top-level README.

The five existing implementations (Python, TypeScript, Java, Go) are
reference examples of this workflow.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
