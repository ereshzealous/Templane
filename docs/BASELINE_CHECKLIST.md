# Baseline Checklist

Starting point for testing Templane from zero.

This file captures the repo's current public contract as it exists today in
code plus top-level documentation. It is intentionally conservative: if a claim
is unclear or inconsistent, it is marked as `Needs decision` instead of being
treated as settled.

---

## 1. Project identity

- Name: `templane`
- Tagline: "A protocol for typed template contracts, conformance, and
  cross-engine rendering."
- Core promise: typed schema contracts for existing template engines without
  replacing the engines themselves.
- Primary value props:
  - type-checked template input
  - shared conformance across implementations
  - breaking-change detection for schema evolution

---

## 2. Protocol surface

- Protocol type: language-neutral protocol, not a single runtime library.
- Normative operations:
  - `parse`
  - `check`
  - `generate`
  - `render`
- Normative schema medium: YAML
- Normative conformance mechanism: fixture-driven via `templane-conform`
- Current fixture count in repo: `40`
- Current implementation count in repo: `5`

---

## 3. Canonical schema forms

### Current supported forms in code

- Sidecar form: supported
  - `.schema.yaml`
  - top-level `body:` points to native template file
- Embedded legacy form: supported
  - YAML schema
  - `---`
  - inline body

### Current parser-level reserved keys

- `body`
- `engine`

### Current body-path rules in code

- must be relative
- must not escape parent directory using `..`
- absolute paths rejected

### Current engine identifiers accepted in code

- `jinja`
- `handlebars`
- `freemarker`
- `gotemplate`
- `markdown`
- `html-raw`
- `yaml-raw`

### Current extension-based engine inference in code

- `.jinja`, `.jinja2`, `.j2` -> `jinja`
- `.hbs`, `.handlebars` -> `handlebars`
- `.ftl`, `.ftlh` -> `freemarker`
- `.tmpl`, `.gotmpl` -> `gotemplate`
- `.md`, `.markdown` -> `markdown`
- `.html`, `.htm` -> `html-raw`
- `.yaml`, `.yml` -> `yaml-raw`

### Needs decision

- Canonical user-facing format:
  - sidecar `.schema.yaml` appears to be the intended default
  - embedded `.templane` still appears in some docs and examples
- Spec/version language around when embedded became "legacy" is inconsistent

---

## 4. Implementations in repo

- `templane-spec/templane-core`
  - Python reference implementation
  - protocol/reference role
- `templane-python`
  - Python production implementation
  - Jinja2 binding: `jinja_templane`
- `templane-ts`
  - TypeScript implementation
  - Handlebars binding
  - `xt` CLI
- `templane-java`
  - Java 21 implementation
  - FreeMarker binding
- `templane-go`
  - Go implementation
  - no dedicated engine wrapper yet; stdlib integration pattern

---

## 5. Conformance baseline

- Expected matrix language count: `5`
- Expected fixture pass count per implementation: `40/40`
- Expected total fixture passes for full matrix: `200`
- Conformance runner: `templane-spec/templane-conform`
- Adapter transport:
  - line-delimited JSON
  - stdin request / stdout response

---

## 6. Test-count baseline

These are the numbers currently claimed by docs and backed by the test trees.

- `templane-spec/templane-core`: `55`
- `templane-python`: `75`
- `templane-ts`: `81`
- `templane-java`: `65`
- `templane-go`: `56`
- Total: `332`

---

## 7. Public tooling baseline

### `templane-conform`

- Purpose: conformance runner for implementations
- Audience: protocol implementers / maintainers
- Status: real, present, central to repo

### `xt`

- Purpose in docs: developer CLI for render/check/test/dev/build
- Backed in code: yes
- Current implementation reality:
  - `render` and `check` load schema via path
  - data input is JSON only
  - `test` scans `.hbs` files, not `.schema.yaml`
  - `dev` compiles inline-body source directly, not sidecar-first
  - `build` expects `---` inline body inside `.hbs`

### Needs decision

- What `xt` officially supports today:
  - sidecar-first schema workflows everywhere
  - or mixed/legacy inline workflows for some commands
- Whether YAML data files are officially supported by `xt`

---

## 8. Packaging baseline

### Python

- `templane-python`
  - package metadata present
  - install story in docs looks like a Python package
- `templane-core`
  - package metadata present

### TypeScript

- `templane-ts/package.json` exists
- package is currently marked `"private": true`
- `xt` bin is defined

### Java

- Gradle multi-module build present
- `maven-publish` configured
- docs present Maven/Gradle coordinates

### Go

- module path present: `github.com/ereshzealous/Templane/templane-go`

### Needs decision

- Which artifacts are officially published now vs intended later
- Whether docs should describe:
  - source-build usage only
  - local publish usage
  - public package-manager install usage

---

## 9. Public testing scope from zero

A clean validation pass should prove all of the following:

- repo bootstrap works per implementation
- each implementation's tests run successfully
- full 5-implementation conformance matrix passes
- each example runs exactly as documented
- install/quickstart docs are reproducible
- package/import names in docs match actual consumable surface
- sidecar vs embedded messaging is consistent
- JSON vs YAML data-file expectations are explicit and correct

---

## 10. Immediate decisions required before full docs validation

- Decide the canonical spec version string used everywhere.
- Decide the canonical schema format for new users.
- Decide whether `xt` is part of the official public surface today or still
  transitional.
- Decide whether YAML data validation via `xt` is in-scope now or future work.
- Decide whether package-install docs should be public-package docs or
  source-build docs.

---

## 11. Suggested test order

1. Validate `SPEC.md` version and schema-form messaging.
2. Validate parser behavior in all 5 implementations.
3. Validate per-language unit tests.
4. Validate conformance matrix.
5. Validate `xt` command behavior against docs.
6. Validate examples exactly as written.
7. Validate installation/quickstart claims.

---

## 12. Exit criteria for the next phase

Phase 1 is complete when:

- this checklist has no unresolved contradictions in the project's intended
  public contract, or
- unresolved items are explicitly marked as intentional and non-blocking.

Only after that should the docs be judged as accurate or inaccurate against the
baseline.
