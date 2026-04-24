# templane-ts

**TypeScript/JavaScript implementation of [Templane](https://github.com/ereshzealous/Templane)** — typed template contracts for Handlebars and other engines, plus the `xt` developer CLI.

Templane adds compile-time schema validation to your templates. Define what data your template expects in a small `.schema.yaml` file next to your `.hbs` / `.jinja` / `.ftl` / `.tmpl`, and Templane catches missing fields, typos, and wrong types **before the engine renders**, not at 2am in production.

- **Conformance:** 40/40 fixtures across the [Templane protocol](https://github.com/ereshzealous/Templane/blob/main/SPEC.md) · 81 unit tests
- **Engine binding:** Handlebars (`handlebars-templane`)
- **CLI:** `xt` (render, check, test, dev, build)
- **Runtime:** Node.js 20+
- **License:** Apache 2.0

---

## Install

```bash
npm install templane-ts
```

This installs:
- The **library** (import from `handlebars-templane` submodule)
- The **`xt` CLI** (available in `./node_modules/.bin/xt` or via `npx xt`)

---

## Quick start

Create `email.hbs` (plain Handlebars — not modified by Templane):

```handlebars
Hi {{user.name}}! Your order #{{order_id}} total is ${{amount}}.
```

Create `email.schema.yaml` next to it (declares the data contract):

```yaml
body: ./email.hbs
engine: handlebars

user:
  type: object
  required: true
  fields:
    name: { type: string, required: true }
order_id:
  type: string
  required: true
amount:
  type: number
  required: true
```

Use from TypeScript:

```typescript
import { compileFromPath, TemplaneHandlebarsError } from 'handlebars-templane';

const tmpl = await compileFromPath('templates/email.schema.yaml');

try {
  const output = tmpl.render({
    user: { name: 'Alice' },
    order_id: 'INV-042',
    amount: 99.00,
  });
  console.log(output);
  // → "Hi Alice! Your order #INV-042 total is $99."
} catch (err) {
  if (err instanceof TemplaneHandlebarsError) {
    for (const e of err.errors) {
      console.error(`[${e.code}] ${e.field}: ${e.message}`);
    }
  }
}
```

---

## Validation errors — caught before rendering

```typescript
// Missing field + wrong type all trip at once
try {
  tmpl.render({
    // user missing entirely
    order_id: 42,      // wrong type
    amount: 'free',    // wrong type
  });
} catch (err) {
  if (err instanceof TemplaneHandlebarsError) {
    err.errors.forEach(e => console.error(`[${e.code}] ${e.field}`));
  }
}
// [missing_required_field] user
// [type_mismatch] order_id
// [type_mismatch] amount
```

All errors are collected — never short-circuits at the first.

---

## The `xt` CLI

Installed alongside the library. Five subcommands:

```bash
xt render <schema.yaml> <data.json>   # render to stdout
xt check  <schema.yaml> <data.json>   # validate only; exit 1 on error
xt test   <templates-dir>             # smoke-test all schemas in a dir
xt dev    <schema.yaml> <data.json>   # watch + re-render on save
xt build  <templates-dir> --out bundle.js   # precompile bodies for production
```

Example CI gate:

```yaml
# .github/workflows/validate.yml
- run: npx xt check templates/welcome.schema.yaml templates/welcome.example.json
```

One-line block of a bad-data PR.

---

## API

```typescript
import { compile, compileFromPath, TemplaneHandlebarsError, TemplaneTemplate } from 'handlebars-templane';

// Compile from a string (for schemas already in memory, or legacy inline-body form)
compile(source: string, schemaId?: string): TemplaneTemplate

// Compile from a file path — follows `body:` references to external template files
compileFromPath(path: string): Promise<TemplaneTemplate>

interface TemplaneTemplate {
  schema: TypedSchema;
  body: string;
  check(data: Record<string, unknown>): TypeCheckError[];  // returns errors without throwing
  render(data: Record<string, unknown>): string;           // throws TemplaneHandlebarsError on invalid data
}
```

Lower-level core functions are also available:

```typescript
import { parse, loadFromPath, check, generate } from 'templane-ts';
// parse(yamlStr, id) → ParseResult
// loadFromPath(path) → Promise<ParseResult>
// check(schema, data) → TypeCheckError[]
// generate(ast, data, schemaId, templateId) → TIRResult
```

---

## Why Templane

Templates are untyped contracts. They accept a bag of values, look up names by string, and render *something* — even when the data has a typo, a missing field, or a wrong type. The failure is silent: the render succeeds, the customer gets a broken email, and you find out four days later.

Templane fixes this at the boundary. A schema next to your template declares what the template expects; the binding refuses to render when the data doesn't match. See the [main README](https://github.com/ereshzealous/Templane) for the full pitch.

---

## Adoption pattern

**You don't migrate templates.** Your existing `.hbs` files stay as-is. You drop one `.schema.yaml` beside each one:

```
templates/
  welcome.hbs                 ← untouched
  welcome.schema.yaml         ← NEW
  invoice.hbs                 ← untouched
  invoice.schema.yaml         ← NEW
```

Your code switches from `Handlebars.compile()` to `compileFromPath()`. That's the migration.

See the [ADOPTION guide](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md) for per-engine walkthroughs.

---

## Examples

Five worked examples under the repo's [`templane-ts/examples/`](https://github.com/ereshzealous/Templane/tree/main/templane-ts/examples) directory: hello, validation errors, nested objects and lists, Handlebars custom helpers, and a full release-notes generator.

---

## Building from source

```bash
git clone https://github.com/ereshzealous/Templane.git
cd Templane/templane-ts
npm install
npm run build   # produces dist/
npm test        # 81 tests
```

---

## Links

- **Repo**: https://github.com/ereshzealous/Templane
- **Full spec (RFC 2119)**: [SPEC.md](https://github.com/ereshzealous/Templane/blob/main/SPEC.md)
- **Architecture**: [docs/ARCHITECTURE.md](https://github.com/ereshzealous/Templane/blob/main/docs/ARCHITECTURE.md) — 12 Mermaid diagrams
- **Adoption guide**: [docs/ADOPTION.md](https://github.com/ereshzealous/Templane/blob/main/docs/ADOPTION.md)
- **Issues**: [GitHub Issues](https://github.com/ereshzealous/Templane/issues)

## License

Apache License 2.0
