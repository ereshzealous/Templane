# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

The repository contains multiple independently-versioned packages (one per
language implementation, plus the `templane-conform` CLI). Entries below
describe the project as a whole; individual packages inherit these version
tags at release time.

---

## [Unreleased]

*(Nothing yet.)*

---

## 2026-04-27 — first registry releases

This release ships every implementation to its language's package
registry. The protocol spec is unchanged at v1.0; the bumps are
package-level only.

### Published

- **`templane-python` 0.2.0** — [PyPI](https://pypi.org/project/templane-python/).
  `pip install templane-python` / `uv add templane-python`.
- **`templane-ts` 0.2.0** — [npm](https://www.npmjs.com/package/templane-ts).
  `npm install templane-ts`. Includes the `xt` CLI as a `bin` entry.
- **`templane-go` v0.2.0** — [Go module proxy](https://pkg.go.dev/github.com/ereshzealous/Templane/templane-go).
  `go get github.com/ereshzealous/Templane/templane-go@v0.2.0`.
- **`templane-java` 0.4.0** — [Maven Central](https://central.sonatype.com/namespace/io.github.ereshzealous).
  Coordinates: `io.github.ereshzealous:freemarker-templane:0.4.0`
  (plus `templane-core`, `templane-adapter-html`, `templane-adapter-yaml`).
  Java skipped 0.2.0 and 0.3.0 — those tags exist on GitHub from
  earlier release attempts that failed Maven Central validation
  (subproject version inheritance bug, see *Fixed* below).

### Added

- Release workflows now take a `bump_type` choice (`patch` | `minor` |
  `major`) instead of a free-text version string. The next version is
  computed from the latest matching `<package>/v*` git tag.
- `twine check --strict` step in the Python release workflow to catch
  bad PyPI metadata before upload.
- npm provenance + Trusted Publishing (OIDC) for `templane-ts`.

### Fixed

- **`templane-java`**: subprojects in `templane-java/build.gradle.kts`
  hard-coded `project.version = "0.1.0"` inside the `subprojects { }`
  block, overriding the root `gradle.properties`. Bumping the version
  in CI only affected the root JAR; subproject artifacts (`templane-core`,
  `templane-adapter-html`, `templane-adapter-yaml`, `freemarker-templane`)
  kept building at 0.1.0, which Maven Central correctly rejected as
  duplicates. Subprojects now inherit `rootProject.version`.

### Changed

- Repository-wide pass to remove low-signal source comments — section
  dividers, restated-code narration, and similar AI-noise patterns.
  Spec references, doc comments on exported APIs, and non-obvious WHY
  notes were kept.
- `examples/README.md` — collapsed duplicate punch-list / difficulty-map
  tables into one index, dropped the defensive "What you won't find
  here" list.

---

## [0.1.0] — 2026-04-24

Initial public release. Every piece below is original work; the project
starts at 1.0 on the protocol spec and 0.1.0 on each implementation
package.

### Added

**Specification**
- `SPEC.md` — Templane Protocol specification v1.0 (RFC 2119 keywords).
  Defines the type system, schema DSL, wire format, four core operations,
  type-checking semantics, schema-evolution categories, conform-adapter
  protocol, and the conformance criteria.
- 40 normative conformance fixtures under `templane-spec/fixtures/`,
  covering schema parsing (16), type checking (8), IR generation (8),
  and adapter rendering (8).

**Implementations** — all pass 5 × 40/40 cross-impl conformance:
- `templane-spec/templane-core` — Python reference implementation (55 tests).
- `templane-python` — Python production + `jinja_templane` (Jinja2) binding,
  breaking-change detector, schema hash (75 tests).
- `templane-ts` — TypeScript + `handlebars-templane` (Handlebars) binding +
  the user-facing `xt` CLI (render/check/test/dev/build) (81 tests).
- `templane-java` — Java 21 (sealed interfaces + records + Jackson) +
  `freemarker-templane` (FreeMarker) binding, breaking-change detector,
  multi-module Gradle build (65 tests).
- `templane-go` — Go 1.22+ core + stdlib `text/template` pattern,
  breaking-change detector, static-binary conform adapter (56 tests).
- `templane-spec/templane-conform` — Node CLI running the 40-fixture
  conformance matrix against any impl's stdio adapter.

**Schema format**
- YAML schema DSL supporting 7 primitive types: `string`, `number`,
  `boolean`, `null`, `enum`, `list`, `object` (recursive).
- **Default form**: `.schema.yaml` file with `body:` key pointing at a
  native template body (`.jinja` / `.hbs` / `.ftl` / `.tmpl`).
- Legacy form (still parsed): `.templane` file with schema + `---`
  separator + inline body. New work should prefer `.schema.yaml`.
- Optional `engine:` key (`jinja` | `handlebars` | `freemarker` |
  `gotemplate` | `markdown` | `html-raw` | `yaml-raw`), inferred from
  body extension when absent.

**Tooling**
- `xt` CLI shipped with `templane-ts`: `render`, `check`, `test`, `dev`,
  `build` subcommands. Accepts both schema forms.
- Breaking-change detector (Python, Java, Go) — four categories:
  `removed_field`, `required_change`, `type_change`, `enum_value_removed`.

**Documentation**
- Root `README.md` — project pitch + story + impl matrix.
- `docs/GETTING_STARTED.md` — zero-to-running walkthrough covering all
  5 implementations plus both CLIs.
- `docs/ADOPTION.md` — per-engine migration guide for existing
  Jinja/Handlebars/FreeMarker/Go-template/Helm codebases.
- `docs/ARCHITECTURE.md` — 12 Mermaid diagrams.
- 22 runnable examples: 6 per-language (in each impl's `examples/`) +
  6 cross-cutting root examples (invoice, audit log, CI workflow,
  multi-env K8s, order email, feature flags) + the Helm chart
  validation flagship.
- Each publishable package ships a registry-audience README.
- Apache 2.0 license applied at the repo root and in each subdirectory.

**CI / release**
- GitHub Actions workflow-dispatch CI that runs per-language tests in
  parallel + the 5-way cross-implementation conformance matrix.
- Release workflows for each registry (npm, PyPI, Maven Central / GitHub
  Packages, Go proxy via git tags). All manually triggered; no secrets
  wired yet.

**Branding**
- Visual identity under `brand/`: `{ T }` mark in 4 color variants,
  wordmark, favicon, 1280×640 social card. README auto-swaps
  light/dark via `<picture>` + `prefers-color-scheme`.

### Not included (deferred)

- `gotmpltemplane` engine binding package for Go (planned; directory
  stub exists but empty). Current Go usage pairs `core.Check` with
  stdlib `text/template`.
- TypeScript port of the breaking-change detector (Python/Java/Go have
  it).
- Maven Central publishing credentials / OSSRH setup. Artifacts-only
  and GitHub-Packages release modes are wired; Maven Central mode
  requires account + GPG signing.
- Published builds on npm, PyPI, Maven Central, proxy.golang.org.
  Workflows are ready; triggering is a separate step.

---

[Unreleased]: https://github.com/ereshzealous/Templane/compare/templane-java/v0.4.0...HEAD
[0.1.0]: https://github.com/ereshzealous/Templane/releases/tag/v0.1.0
