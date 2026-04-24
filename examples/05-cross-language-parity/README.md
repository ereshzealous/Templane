# 05 — Cross-Language Parity ⭐⭐⭐⭐

Demonstrates: **the same Templane input produces byte-identical TIR output
across all 5 language implementations.**

This is the strongest single-shot proof that Templane is a protocol, not
a library: Python's `tsp_core`, TypeScript's `templane-core`, Java's
`dev.templane.core`, Go's `core`, and the reference implementation all
agree on every edge case in the 32 conformance fixtures.

## How the proof works

The `templane-conform` CLI feeds each fixture through every registered
adapter and **compares outputs for deep equality**. If any two adapters
produce different output for any fixture, the CLI reports a mismatch and
exits non-zero.

This directory doesn't add new files — the proof is already wired up via
the standard conformance run.

## Run the full matrix

From the repo root (after the Common Setup in `examples/README.md`):

```bash
node templane-spec/templane-conform/dist/cli.js \
  --adapters \
    "spec:python3 templane-spec/conform-adapter/run.py" \
    "ts:node templane-ts/dist/conform-adapter.js" \
    "py:python3 templane-python/conform-adapter/run.py" \
    "java:templane-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar" \
    "go:templane-go/bin/conform-adapter"
```

Expected output:

```
Running 40 fixture(s) across 5 implementation(s)...
  ✓ spec:   40/40
  ✓ ts:     40/40
  ✓ py:     40/40
  ✓ java:   40/40
  ✓ go:     40/40
All implementations conformant.
```

**40 × 5 = 200 fixture comparisons. All pass.**

## What "byte-identical" means here

Each fixture has an `expected_output`. Each adapter transforms the input
through its language's implementation and returns JSON. The CLI checks
**deep JSON equality** (order-insensitive for objects, order-sensitive
for arrays) against the expected output.

### Fixture example: `adapters/html-foreach`

**Input** (the same payload sent to all 5 adapters):

```json
{
  "tir": {
    "template_id": "list", "schema_id": "data",
    "nodes": [{
      "kind": "foreach", "var": "item",
      "items": [
        [{"kind": "text", "content": "<li>"}, {"kind": "expr", "field": "item", "resolved": "apple"},  {"kind": "text", "content": "</li>"}],
        [{"kind": "text", "content": "<li>"}, {"kind": "expr", "field": "item", "resolved": "banana"}, {"kind": "text", "content": "</li>"}]
      ]
    }]
  }
}
```

**Expected output** (required from every implementation):

```
<!-- templane template_id=list schema_id=data -->
<li>apple</li><li>banana</li>
```

Every implementation must produce **exactly this byte string**. If
Python's `html.escape()` and Java's Jackson-backed serialization and
Go's `strings.ReplaceAll` all don't agree, the fixture fails. (They do.)

## Why this matters

1. **No drift.** A team that picks `templane-python` today can migrate
   half their pipeline to `templane-go` tomorrow and get identical
   behavior on every template.

2. **Interop guaranteed.** A template + schema authored against `templane-java`
   will render identically in `templane-ts` — enabling designer/developer
   workflows that cross language boundaries.

3. **Regression prevention.** CI runs this matrix on every PR to every
   implementation. Any divergence blocks the merge automatically.

4. **Protocol credibility.** JSON Schema, Protobuf, and Avro all have
   similar conformance suites. Any schema system without one has no
   credible claim to cross-language portability.

## Pick a single fixture + single language to inspect manually

```bash
echo '{"fixture_id":"schema-parser/basic","fixture":{"id":"basic","yaml":"name:\n  type: string\n  required: true\n"}}' \
  | python3 templane-spec/conform-adapter/run.py
```

Output:
```json
{"output": {"schema": {"id": "basic", "fields": {"name": {"name": "name", "type": {"kind": "string"}, "required": true}}}}}
```

Now the Java adapter on the same fixture:

```bash
echo '{"fixture_id":"schema-parser/basic","fixture":{"id":"basic","yaml":"name:\n  type: string\n  required: true\n"}}' \
  | java -jar templane-java/conform-adapter/build/libs/conform-adapter-0.1.0.jar
```

Output:
```json
{"output":{"schema":{"id":"basic","fields":{"name":{"name":"name","type":{"kind":"string"},"required":true}}}}}
```

Same structure. Whitespace differs (that's fine — JSON equality is
semantic, not string-level) but every key/value is identical.

## What to take away

- The protocol has **40 fixtures** × **5 languages** = **200 enforced
  equality checks**.
- Cross-language parity isn't a promise — it's a build-time constraint.
- If you add a 6th language, you run the same suite and get the same
  guarantee. No special-case edits.

→ Next: [`06-helm-chart-validation`](../06-helm-chart-validation/) — real-world
application: type-check Kubernetes Helm `values.yaml` before deploying.
