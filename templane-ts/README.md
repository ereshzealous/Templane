# templane-ts

TypeScript implementation of [Templane](../SPEC.md). Ships a full **Handlebars
integration** (`handlebars-templane`) and the `xt` CLI (`render`, `check`, `test`,
`dev`, `build`).

**Conformance:** `ts 32/32` ✓ — **64 unit tests passing**.

---

## Installation

```bash
cd templane-ts
npm install
npm run build    # compiles to dist/
```

Requires Node.js 20+.

---

## Quick start — programmatic API

```typescript
import { compile } from './src/handlebars-templane';

const source = `
name:
  type: string
  required: true
---
Hello {{name}}!
`;

const template = compile(source, 'greeting');
console.log(template.render({ name: 'Alice' }));
// → Hello Alice!
```

### Separate check and render

```typescript
import { compile } from './src/handlebars-templane';

const template = compile(source, 'greeting');

// Validate data without rendering
const errors = template.check({ naem: 'Alice' });
for (const e of errors) {
  console.error(`[${e.code}] ${e.message}`);
}
// [missing_required_field] Required field 'name' is missing
// [did_you_mean]           Unknown field 'naem'. Did you mean 'name'?

// Render (throws TemplaneHandlebarsError on type errors)
template.render({ name: 'Alice' });
```

---

## The `xt` CLI

Five subcommands:

### `xt render <template> <data.json>`

Render a template with data. Type-checks before rendering.

```bash
node dist/xt.js render templates/greeting.hbs data.json
# Hello Alice!
```

### `xt check <template> <data.json>`

Validate data against the schema without rendering.

```bash
node dist/xt.js check templates/greeting.hbs data.json
# ✓ data matches schema
```

### `xt test <dir>`

Walk a directory, compile every `*.hbs`, and render any with an adjacent
`<name>.example.json`.

```bash
node dist/xt.js test templates/
#   ✓ greeting.hbs (compiled + rendered example)
#   ✓ sub/nested.hbs (compiled)
# 2/2 passed
```

Exits non-zero if any template fails.

### `xt dev <template> <data.json>`

Watch both files, re-render on any change. Template authoring REPL.

```bash
node dist/xt.js dev greeting.hbs data.json
```

### `xt build <dir> --out <file>`

Precompile every `*.hbs` to a single CommonJS module (no runtime parsing).

```bash
node dist/xt.js build templates/ --out dist/templates.cjs
# Built 12 template(s) → dist/templates.cjs
```

The generated module exports a map `{name: compiledFn}`. Requires
`handlebars/runtime` as a peer dep at runtime.

---

## Handlebars-templane API

```typescript
import { compile, TemplaneHandlebarsError } from './src/handlebars-templane';

interface TemplaneTemplate {
  schema: TypedSchema;
  body: string;
  check(data: Record<string, unknown>): TypeCheckError[];
  render(data: Record<string, unknown>): string;  // throws TemplaneHandlebarsError
}

function compile(source: string, schemaId?: string): TemplaneTemplate;
```

`TemplaneHandlebarsError.errors` is the raw `TypeCheckError[]` array.

---

## Running tests

```bash
npm test
# 64 tests, 0 failures
```

Tests cover: schema parser (7), type checker (9), IR generator (9), adapters
(10), handlebars-templane (12), xt CLI (17).

---

## Design notes

### Discriminated unions via TypeScript

Templane's AST and TIR types are discriminated unions. TypeScript's native
discriminated-union support (`type Node = {kind: "text", ...} | {kind: "expr", ...}`)
means the JSON wire format *is* the type — no `to_dict`/`from_dict`
serialization helpers needed. Contrast with Python (dataclasses) and Java
(sealed interfaces + records).

### Zero test framework deps

Uses Node 20+'s built-in `node:test` + `node:assert/strict` via `tsx` for
TypeScript support. No Jest, no Vitest.

### Production deps: 2

Only `js-yaml` and `handlebars`. Everything else is stdlib.

---

## License

Apache License 2.0. See [LICENSE](../LICENSE).
