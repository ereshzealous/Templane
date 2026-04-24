# templane-conform

**The conformance test runner for [Templane](https://github.com/ereshzealous/Templane).**

`templane-conform` is a small Node CLI that drives the Templane conformance suite: 40 JSON fixtures covering schema parsing, type checking, IR generation, and adapter rendering. You point it at one or more Templane implementations (via a subprocess command) and it reports pass/fail per fixture.

**Who needs this:** people building or maintaining a Templane implementation in a new language. Day-to-day Templane users don't — they use their language's binding (`templane-ts`, `templane-python`, etc.).

- **Runtime:** Node.js 20+
- **License:** Apache 2.0

---

## Install

```bash
npm install -g templane-conform
# or without a global install:
npx templane-conform --help
```

---

## Quick start

Test a single implementation's conform-adapter:

```bash
templane-conform \
  --adapters "myimpl:./path/to/my-conform-adapter"
```

Test multiple implementations at once (the real conformance matrix):

```bash
templane-conform \
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

---

## The adapter protocol

Every implementation ships a "conform adapter" — a program that reads
fixture inputs on stdin and writes results on stdout, one JSON line at
a time. Your adapter stays alive across all 40 fixtures (so JVM/Node
startup cost is paid once).

**Protocol** (line-delimited JSON):

```
→ stdin:   {"fixture_id": "schema-parser/basic", "fixture": {...}}
← stdout:  {"output": {...}}      // on success
← stdout:  {"output": null, "error": "..."}   // on failure
```

Your adapter dispatches on `fixture_id` prefix: `schema-parser/`,
`type-checker/`, `ir-generator/`, `adapters/html`, `adapters/yaml`.

See [SPEC §9](https://github.com/ereshzealous/Templane/blob/main/SPEC.md#9-conform-adapter-protocol) for the normative definition.

---

## Writing a new Templane implementation

Starting a Templane impl for a language we don't support yet? The
conformance loop is your forcing function:

1. Read [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md) (RFC 2119-style, versioned 1.0).
2. Implement `parse`, `check`, `generate`, plus the HTML/YAML adapters.
3. Write your conform-adapter (line-delimited JSON on stdin/stdout).
4. Run `templane-conform --adapters "myimpl:./my-adapter"`.
5. Iterate on any failing fixture until you reach 40/40.

See existing implementations in the repo — Python, TypeScript, Java, Go — for templates to mirror.

---

## Why

Cross-implementation conformance is the single load-bearing property that makes Templane a protocol, not a library. Five independently-written impls all passing the same 40 fixtures is the proof that the spec is unambiguous and that each binding behaves identically across languages.

See the [main README](https://github.com/ereshzealous/Templane) for the full picture.

---

## Building from source

```bash
git clone https://github.com/ereshzealous/Templane.git
cd Templane/templane-spec/templane-conform
npm install
npm run build   # produces dist/
```

---

## Links

- **Repo**: https://github.com/ereshzealous/Templane
- **Full spec**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- **Adapter protocol (§9)**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md#9-conform-adapter-protocol)
- **Fixtures**: [templane-spec/fixtures/](https://github.com/ereshzealous/Templane/tree/main/templane-spec/fixtures)
- **Issues**: [GitHub Issues](https://github.com/ereshzealous/Templane/issues)

## License

Apache License 2.0
